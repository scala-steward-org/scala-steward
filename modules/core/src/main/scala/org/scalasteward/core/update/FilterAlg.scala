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

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.data.*
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.FilterAlg.*
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class FilterAlg[F[_]](implicit
    logger: Logger[F],
    F: Monad[F]
) {
  def localFilterSingle(
      config: RepoConfig,
      update: ArtifactUpdateCandidates
  ): F[Option[Update.ForArtifactId]] =
    localFilter(update, config) match {
      case Right(update) => F.pure(update.some)
      case Left(reason)  =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})").as(None)
    }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, ArtifactUpdateCandidates]

  sealed trait RejectionReason {
    def update: ArtifactUpdateVersions[?]
    def show: String =
      this match {
        case IgnoredByConfig(_)         => "ignored by config"
        case VersionPinnedByConfig(_)   => "version is pinned by config"
        case NotAllowedByConfig(_)      => "not allowed by config"
        case NoSuitableNextVersion(_)   => "no suitable next version"
        case VersionOrderingConflict(_) => "version ordering conflict"
        case IgnoreScalaNext(_)         => "not upgrading from Scala LTS to Next version"
      }
  }

  final case class IgnoredByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionPinnedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NotAllowedByConfig(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class NoSuitableNextVersion(update: ArtifactUpdateCandidates) extends RejectionReason
  final case class VersionOrderingConflict(update: Update.ForArtifactId) extends RejectionReason
  final case class IgnoreScalaNext(update: ArtifactUpdateCandidates) extends RejectionReason

  def localFilter(
      update: ArtifactUpdateCandidates,
      repoConfig: RepoConfig
  ): Either[RejectionReason, Update.ForArtifactId] =
    repoConfig.updatesOrDefault
      .keep(update)
      .flatMap(scalaLTSFilter)
      .flatMap(globalFilter(_, repoConfig))

  def scalaLTSFilter(update: ArtifactUpdateCandidates): FilterResult =
    if (!isScala3Lang(update.artifactForUpdate))
      Right(update)
    else {
      if (update.artifactForUpdate.currentVersion >= scalaNextMinVersion) {
        // already on Scala Next
        Right(update)
      } else {
        val filteredVersions =
          update.newerVersionsWithFirstSeen.filterNot(v => v.version >= scalaNextMinVersion)
        if (filteredVersions.nonEmpty)
          Right(update.copy(newerVersionsWithFirstSeen = Nel.fromListUnsafe(filteredVersions)))
        else
          Left(IgnoreScalaNext(update))
      }
    }

  def isScala3Lang(artifactForUpdate: ArtifactForUpdate): Boolean =
    scala3LangModules.exists { case (g, a) =>
      artifactForUpdate.groupId == g && artifactForUpdate.artifactId.name == a.name
    }

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
      update: Update.ForArtifactId
  ): Either[RejectionReason, Update.ForArtifactId] = {
    val current = coursier.core.Version(update.currentVersion.value)
    val next = coursier.core.Version(update.nextVersion.value)
    if (current > next) Left(VersionOrderingConflict(update)) else Right(update)
  }
}
