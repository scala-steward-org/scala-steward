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
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.*
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.FilterAlg.*
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vulnerability.VulnerabilityCheckAlg
import org.typelevel.log4cats.Logger

final class FilterAlg[F[_]](implicit
    coursierAlg: CoursierAlg[F],
    logger: Logger[F],
    dateTimeAlg: DateTimeAlg[F],
    vulnerabilityCheckAlg: VulnerabilityCheckAlg[F],
    F: MonadThrow[F]
) {
  def localFilterSingle(
      config: RepoConfig,
      update: ArtifactUpdateCandidates,
      resolvers: List[Resolver]
  ): F[Option[Update.ForArtifactId]] = for {
    currentTime <- dateTimeAlg.currentTimestamp
    scalaVersionLine <- findScalaVersionLines(config, update, resolvers)
    baseline = localFilter(update, config, currentTime, cooldownExempt = None, scalaVersionLine)
    vulnerabilityFix <-
      vulnerabilityFixInCooldown(config, update, currentTime, baseline.toOption, scalaVersionLine)
    result <- vulnerabilityFix.fold(baseline)(_.asRight) match {
      case Right(update) => F.pure(update.some)
      case Left(reason)  =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})").as(None)
    }
  } yield result

  // Scala releases a whole series as LTS or Next, so one POM per series is enough.
  private def findScalaVersionLines(
      config: RepoConfig,
      update: ArtifactUpdateCandidates,
      resolvers: List[Resolver]
  ): F[Version => Option[ScalaVersionLine]] = {
    val current = update.artifactForUpdate.currentVersion
    val currentSeries = scalaSeries(current)
    val versionsBySeries = (current :: update.newerVersions.toList).groupBy(scalaSeries)

    def findLine(series: List[Version.Component]): F[Option[ScalaVersionLine]] =
      versionsBySeries(series).filterNot(_.isSnapshot).maximumOption.flatTraverse { version =>
        UpdateAlg.dependencyPairs(update.artifactForUpdate, version).headOption.flatTraverse {
          case (_, dependency) =>
            coursierAlg
              .getMetadata(dependency, resolvers)
              .map(_.scalaVersionLine.flatMap(ScalaVersionLine.parse))
        }
      }

    if (
      !isScala3Lang(update) || !current.value.startsWith("3.") || versionsBySeries.sizeIs <= 1 ||
      config.updatesOrDefault.allowScalaNextOrDefault
    )
      F.pure(unknownScalaVersionLine)
    else
      findLine(currentSeries).flatMap {
        case Some(ScalaVersionLine.Next) =>
          F.pure(version =>
            Option.when(scalaSeries(version) === currentSeries)(ScalaVersionLine.Next)
          )
        case currentLine =>
          (versionsBySeries.keySet - currentSeries).toList
            .traverse(series => findLine(series).tupleLeft(series))
            .map { lines =>
              val lineBySeries = ((currentSeries -> currentLine) :: lines).toMap
              version => lineBySeries.get(scalaSeries(version)).flatten
            }
      }
  }

  private def vulnerabilityFixInCooldown(
      config: RepoConfig,
      update: ArtifactUpdateCandidates,
      currentTime: Timestamp,
      baseline: Option[Update.ForArtifactId],
      scalaVersionLine: Version => Option[ScalaVersionLine]
  ): F[Option[Update.ForArtifactId]] = {
    // Updates to cooldown versions that win selection once exempted.
    val pairsByExempted = config.updatesOrDefault
      .versionsInCooldown(update, currentTime, config.dependencyOverridesOrDefault)
      .flatMap { version =>
        localFilter(
          update,
          config,
          currentTime,
          cooldownExempt = version.some,
          scalaVersionLine
        ).toOption
          .filter(_.nextVersion === version)
      }
      .map(exempted => exempted -> dependencyPairs(exempted))
    val vulnerabilityCheck = config.vulnerabilityCheckOrDefault
    val isExcluded = pairsByExempted.exists { case (_, pairs) =>
      pairs.exists { case (current, next) =>
        vulnerabilityCheck.isExcluded(current) || vulnerabilityCheck.isExcluded(next)
      }
    }
    if (pairsByExempted.isEmpty || isExcluded) F.pure(None)
    else selectVulnerabilityFix(update, baseline, pairsByExempted)
  }

  // Picks the cooldown version fixing most open advisories, lowest on ties.
  private def selectVulnerabilityFix(
      update: ArtifactUpdateCandidates,
      baseline: Option[Update.ForArtifactId],
      pairsByExempted: List[(Update.ForArtifactId, List[(Dependency, Dependency)])]
  ): F[Option[Update.ForArtifactId]] = {
    val baselinePairs = baseline.toList.flatMap(dependencyPairs)
    // Without a baseline update, the current dependency stays.
    val nextInBaseline = baselinePairs.toMap.withDefault(identity)
    val comparisonsByExempted = pairsByExempted.map { case (exempted, pairs) =>
      exempted -> pairs.map { case (current, next) => nextInBaseline(current) -> next }
    }
    val comparisons = comparisonsByExempted.flatMap(_._2).distinct
    vulnerabilityCheckAlg.compare(comparisons).flatMap { idsByPair =>
      // Only advisories the baseline would leave or introduce count as fixes.
      val fixedIdsByExempted = comparisonsByExempted
        .map { case (exempted, pairs) =>
          exempted -> pairs.foldMap(pair => idsByPair(pair).fixedIds)
        }
        .filter { case (_, fixedIds) => fixedIds.nonEmpty }
      fixedIdsByExempted.minimumByOption { case (exempted, fixedIds) =>
        (-fixedIds.size, exempted.nextVersion)
      } match {
        case Some((exempted, fixedIds)) =>
          val artifact = update.artifactForUpdate
          val version = exempted.nextVersion
          val coordinates =
            s"${artifact.groupId}:${artifact.crossDependency.showArtifactNames}:$version"
          logger
            .info(
              s"Skip cooldown for $coordinates (fixes ${fixedIds.toList.sorted.mkString(", ")})"
            )
            .as(exempted.some)
        case None => F.pure(None)
      }
    }
  }

  private def dependencyPairs(update: Update.ForArtifactId): List[(Dependency, Dependency)] =
    UpdateAlg.dependencyPairs(update.artifactForUpdate, update.nextVersion)
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
        case IgnoreScalaNext(_)         =>
          "not upgrading from Scala LTS to Next version; set updates.allowScalaNext = true to allow"
        case TooRecentForCooldown(_) => "too recent for cooldown config"
      }
  }

  final case class IgnoredByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class TooRecentForCooldown(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionPinnedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NotAllowedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NoSuitableNextVersion(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionOrderingConflict(update: Update.ForArtifactId) extends RejectionReason
  final case class IgnoreScalaNext(update: ArtifactUpdateCandidates) extends RejectionReason

  sealed trait ScalaVersionLine
  object ScalaVersionLine {
    case object Lts extends ScalaVersionLine
    case object Next extends ScalaVersionLine

    def parse(value: String): Option[ScalaVersionLine] =
      value.toUpperCase match {
        case "LTS"  => Some(Lts)
        case "NEXT" => Some(Next)
        case _      => None
      }
  }

  val unknownScalaVersionLine: Version => Option[ScalaVersionLine] = _ => None

  def localFilter(
      update: ArtifactUpdateCandidates,
      repoConfig: RepoConfig,
      currentTime: Timestamp,
      cooldownExempt: Option[Version],
      scalaVersionLine: Version => Option[ScalaVersionLine] = unknownScalaVersionLine
  ): Either[RejectionReason, Update.ForArtifactId] =
    repoConfig.updatesOrDefault
      .keep(update, currentTime, repoConfig.dependencyOverridesOrDefault, cooldownExempt)
      .flatMap(
        scalaLTSFilter(_, scalaVersionLine, repoConfig.updatesOrDefault.allowScalaNextOrDefault)
      )
      .flatMap(globalFilter(_, repoConfig))

  private def scalaSeries(version: Version): List[Version.Component] =
    version.alnumComponents.take(2)

  def scalaLTSFilter(
      update: ArtifactUpdateCandidates,
      scalaVersionLine: Version => Option[ScalaVersionLine] = unknownScalaVersionLine,
      allowScalaNext: Boolean = false
  ): FilterResult =
    if (isScala3Lang(update)) {
      val current = update.artifactForUpdate.currentVersion
      if (current.value.startsWith("2.")) {
        // no updates from Scala 2 to Scala 3; newer Scala 2 versions stay eligible
        update.filterVersions(_.value.startsWith("2.")).toRight(NoSuitableNextVersion(update))
      } else if (scalaVersionLine(current).contains(ScalaVersionLine.Next)) {
        // already on Scala Next
        Right(update)
      } else if (allowScalaNext) {
        // on Scala LTS, but opted in to Scala Next
        Right(update)
      } else {
        // on Scala LTS, or the line is unknown because the POM could not be fetched
        update
          .filterVersions { version =>
            scalaSeries(version) === scalaSeries(current) ||
            scalaVersionLine(version).contains(ScalaVersionLine.Lts)
          }
          .toRight(IgnoreScalaNext(update))
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
