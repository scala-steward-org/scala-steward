/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.update

import cats.MonadThrow
import cats.syntax.all.*
import org.scalasteward.core.data.*
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.FilterAlg.*
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vulnerability.OsvCache
import org.typelevel.log4cats.Logger

final class FilterAlg[F[_]](enableVulnerabilityCheck: Boolean)(implicit
    logger: Logger[F],
    dateTimeAlg: DateTimeAlg[F],
    osvCache: OsvCache[F],
    F: MonadThrow[F]
) {
  def localFilterSingle(
      config: RepoConfig,
      update: ArtifactUpdateCandidates
  ): F[Option[Update.ForArtifactId]] = for {
    currentTime <- dateTimeAlg.currentTimestamp
    cooldownExempt <- vulnerabilityFixesInCooldown(config, update, currentTime)
    result <- localFilter(update, config, currentTime, cooldownExempt) match {
      case Right(update) => F.pure(update.some)
      case Left(reason)  =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})").as(None)
    }
  } yield result

  private def vulnerabilityFixesInCooldown(
      config: RepoConfig,
      update: ArtifactUpdateCandidates,
      currentTime: Timestamp
  ): F[Set[Version]] =
    if (!enableVulnerabilityCheck) F.pure(Set.empty)
    else {
      val pairsByVersion = config.updatesOrDefault
        .versionsInCooldown(update, currentTime, config.dependencyOverridesOrDefault)
        // Exempting a version that would still lose selection is pointless.
        .filter(version =>
          localFilter(update, config, currentTime, Set(version)).exists(_.nextVersion === version)
        )
        .map(version => version -> UpdateAlg.dependencyPairs(update.artifactForUpdate, version))
      val vulnerabilityCheck = config.vulnerabilityCheckOrDefault
      val isExcluded = pairsByVersion.exists { case (_, pairs) =>
        pairs.exists { case (current, next) =>
          vulnerabilityCheck.isExcluded(current) || vulnerabilityCheck.isExcluded(next)
        }
      }
      if (pairsByVersion.isEmpty || isExcluded) F.pure(Set.empty)
      else
        logger.attemptWarn
          .log("Vulnerability check failed")(lowestVulnerabilityFix(update, pairsByVersion))
          .map(_.getOrElse(Set.empty))
    }

  private def lowestVulnerabilityFix(
      update: ArtifactUpdateCandidates,
      pairsByVersion: List[(Version, List[(Dependency, Dependency)])]
  ): F[Set[Version]] = {
    val dependencies = (update.artifactForUpdate.crossDependency.dependencies.toList ++
      pairsByVersion.flatMap { case (_, pairs) => pairs.map { case (_, next) => next } }).distinct
    osvCache.vulnerabilityIds(dependencies).flatMap { dependencyToIds =>
      def idsOf(dependency: Dependency): Set[String] =
        dependencyToIds.getOrElse(dependency, Set.empty)

      val fixedIdsByVersion = pairsByVersion
        .map { case (version, pairs) =>
          version -> pairs.foldMap { case (current, next) => idsOf(current) -- idsOf(next) }
        }
        .filter { case (_, fixedIds) => fixedIds.nonEmpty }
      fixedIdsByVersion.minimumByOption { case (version, _) => version } match {
        case Some((version, fixedIds)) =>
          val artifact = update.artifactForUpdate
          val coordinates =
            s"${artifact.groupId}:${artifact.crossDependency.showArtifactNames}:$version"
          logger
            .info(
              s"Skip cooldown for $coordinates (fixes ${fixedIds.toList.sorted.mkString(", ")})"
            )
            .as(Set(version))
        case None => F.pure(Set.empty)
      }
    }
  }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, ArtifactUpdateCandidates]

  sealed trait RejectionReason {
    def update: ArtifactUpdateVersions
    def show: String =
      this match {
        case IgnoredByConfig(_)         => "ignored by config"
        case VersionPinnedByConfig(_)   => "version is pinned by config"
        case NotAllowedByConfig(_)      => "not allowed by config"
        case NoSuitableNextVersion(_)   => "no suitable next version"
        case VersionOrderingConflict(_) => "version ordering conflict"
        case IgnoreScalaNext(_)         => "not upgrading from Scala LTS to Next version"
        case TooRecentForCooldown(_)    => "too recent for cooldown config"
      }
  }

  final case class IgnoredByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class TooRecentForCooldown(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionPinnedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NotAllowedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NoSuitableNextVersion(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionOrderingConflict(update: Update.ForArtifactId) extends RejectionReason
  final case class IgnoreScalaNext(update: ArtifactUpdateCandidates) extends RejectionReason

  def localFilter(
      update: ArtifactUpdateCandidates,
      repoConfig: RepoConfig,
      currentTime: Timestamp,
      cooldownExempt: Set[Version]
  ): Either[RejectionReason, Update.ForArtifactId] =
    repoConfig.updatesOrDefault
      .keep(update, currentTime, repoConfig.dependencyOverridesOrDefault, cooldownExempt)
      .flatMap(scalaLTSFilter)
      .flatMap(globalFilter(_, repoConfig))

  private def isScalaLtsVersion(version: Version): Boolean =
    version.value.startsWith("3.3.") || version.value.startsWith("3.9.")

  def scalaLTSFilter(update: ArtifactUpdateCandidates): FilterResult =
    if (isScala3Lang(update)) {
      if (
        update.artifactForUpdate.currentVersion.value.startsWith("3.") &&
        !isScalaLtsVersion(update.artifactForUpdate.currentVersion)
      ) {
        // already on Scala Next
        Right(update)
      } else if (update.artifactForUpdate.currentVersion.value.startsWith("2.")) {
        // no updates from Scala 2 to Scala 3; newer Scala 2 versions stay eligible
        update.filterVersions(_.value.startsWith("2.")).toRight(NoSuitableNextVersion(update))
      } else {
        // on Scala LTS (3.3.x or 3.9.x), stay on LTS versions
        update.filterVersions(isScalaLtsVersion).toRight(IgnoreScalaNext(update))
      }
    } else {
      Right(update)
    }

  def isScala3Lang(update: ArtifactUpdateCandidates): Boolean =
    scala3LangModules.exists { case (g, a) =>
      (update.artifactForUpdate.groupId == g && update.artifactForUpdate.artifactId.name == a.name)
    } || isScalaLibraryFor3(update)

  private def isScalaLibraryFor3(update: ArtifactUpdateCandidates): Boolean =
    (update.artifactForUpdate.groupId == scalaLangGroupId &&
      update.artifactForUpdate.artifactId.name == scalaLibrary.name &&
      update.newerVersions.exists(_ > scala38))

  private def globalFilter(
      update: ArtifactUpdateCandidates,
      repoConfig: RepoConfig
  ): Either[RejectionReason, Update.ForArtifactId] =
    selectSuitableNextVersion(update, repoConfig).flatMap(checkVersionOrdering)

  def isDependencyConfigurationIgnored(dependency: Dependency): Boolean =
    dependency.configurations.fold("")(_.toLowerCase) match {
      case "phantom-js-jetty"    => true
      case "scalafmt"            => true
      case "scripted-sbt"        => true
      case "scripted-sbt-launch" => true
      case "tut"                 => true
      case _                     => false
    }

  private def selectSuitableNextVersion(
      update: ArtifactUpdateCandidates,
      repoConfig: RepoConfig
  ): Either[RejectionReason, Update.ForArtifactId] = {
    val newerVersions = update.newerVersions.toList
    val allowPreReleases = repoConfig.updatesOrDefault.preRelease(update).isRight

    update.artifactForUpdate.currentVersion.selectNext(newerVersions, allowPreReleases) match {
      case Some(next) => Right(update.asSpecificUpdate(nextVersion = next))
      case None       => Left(NoSuitableNextVersion(update))
    }
  }

  private def checkVersionOrdering(
      u: Update.ForArtifactId
  ): Either[RejectionReason, Update.ForArtifactId] =
    Either.cond(u.versionUpdate.obeysCoursierOrdering, u, VersionOrderingConflict(u))
}
