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
      update: Update.ForArtifactId
  ): F[Option[Update.ForArtifactId]] =
    logIfRejected(localFilter(update, config))

  private def logIfRejected(result: FilterResult): F[Option[Update.ForArtifactId]] =
    result match {
      case Right(update) => F.pure(update.some)
      case Left(reason)  =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})").as(None)
    }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, Update.ForArtifactId]

  sealed trait RejectionReason {
    def update: Update.ForArtifactId
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

  final case class IgnoredByConfig(update: Update.ForArtifactId) extends RejectionReason
  final case class VersionPinnedByConfig(update: Update.ForArtifactId) extends RejectionReason
  final case class NotAllowedByConfig(update: Update.ForArtifactId) extends RejectionReason
  final case class NoSuitableNextVersion(update: Update.ForArtifactId) extends RejectionReason
  final case class VersionOrderingConflict(update: Update.ForArtifactId) extends RejectionReason
  final case class IgnoreScalaNext(update: Update.ForArtifactId) extends RejectionReason

  def localFilter(update: Update.ForArtifactId, repoConfig: RepoConfig): FilterResult =
    repoConfig.updatesOrDefault
      .keep(update)
      .flatMap(scalaLTSFilter)
      .flatMap(globalFilter(_, repoConfig))

  def scalaLTSFilter(update: Update.ForArtifactId): FilterResult =
    if (!isScala3Lang(update))
      Right(update)
    else {
      if (update.currentVersion >= scalaNextMinVersion) {
        // already on Scala Next
        Right(update)
      } else {
        val filteredVersions = update.newerVersions.filterNot(_ >= scalaNextMinVersion)
        if (filteredVersions.nonEmpty)
          Right(update.copy(newerVersions = Nel.fromListUnsafe(filteredVersions)))
        else
          Left(IgnoreScalaNext(update))
      }
    }

  def isScala3Lang(update: Update.ForArtifactId): Boolean =
    scala3LangModules.exists { case (g, a) =>
      update.groupId == g && update.artifactIds.exists(_.name == a.name)
    }

  private def globalFilter(update: Update.ForArtifactId, repoConfig: RepoConfig): FilterResult =
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
      update: Update.ForArtifactId,
      repoConfig: RepoConfig
  ): FilterResult = {
    val newerVersions = update.newerVersions.toList
    val maybeNext = repoConfig.updatesOrDefault.preRelease(update) match {
      case Left(_)  => update.currentVersion.selectNext(newerVersions)
      case Right(_) => update.currentVersion.selectNext(newerVersions, allowPreReleases = true)
    }
    maybeNext match {
      case Some(next) => Right(update.copy(newerVersions = Nel.of(next)))
      case None       => Left(NoSuitableNextVersion(update))
    }
  }

  private def checkVersionOrdering(update: Update.ForArtifactId): FilterResult = {
    val current = coursier.core.Version(update.currentVersion.value)
    val next = coursier.core.Version(update.nextVersion.value)
    if (current > next) Left(VersionOrderingConflict(update)) else Right(update)
  }
}
