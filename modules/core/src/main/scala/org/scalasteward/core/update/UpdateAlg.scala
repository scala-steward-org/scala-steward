/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.implicits._
import cats.{Monad, Parallel}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.coursier.VersionsCacheFacade
import org.scalasteward.core.data._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import UpdateAlg._

final class UpdateAlg[F[_]](
    implicit
    filterAlg: FilterAlg[F],
    logger: Logger[F],
    parallel: Parallel[F],
    versionsCache: VersionsCacheFacade[F],
    F: Monad[F]
) {
  def findUpdate(dependency: Scope[Dependency], useCache: Boolean): F[Option[Update.Single]] =
    for {
      versions <- {
        if (useCache) versionsCache.getVersions(dependency)
        else versionsCache.getVersionsFresh(dependency)
      }
      current = Version(dependency.value.version)
      maybeNewerVersions = Nel.fromList(versions.filter(_ > current))
      maybeUpdate0 = maybeNewerVersions.map { newerVersions =>
        Update.Single(CrossDependency(dependency.value), newerVersions.map(_.value))
      }
      maybeUpdate1 = maybeUpdate0.orElse(findUpdateWithNewerGroupId(dependency.value))
    } yield maybeUpdate1

  def findUpdates(
      dependencies: List[Scope.Dependency],
      repoConfig: RepoConfig
  ): F[List[Update.Single]] =
    for {
      _ <- logger.info(s"Find updates")
      updates0 <- dependencies.parFlatTraverse(findUpdate(_, useCache = true).map(_.toList))
      updates1 <- filterAlg.localFilterMany(repoConfig, updates0)
      (outOfSyncDependencies, updates2) = extractOutOfSyncDependencies(dependencies, updates1)
      _ = outOfSyncDependencies.foreach(println)
      newUpdates0 <- outOfSyncDependencies.traverseFilter(findUpdate(_, useCache = false))
      newUpdates1 <- filterAlg.localFilterMany(repoConfig, newUpdates0)
      updates3 = Update.groupByArtifactIdName(updates2 ++ newUpdates1)
      _ <- logger.info(util.logger.showUpdates(updates3.widen[Update]))
    } yield updates3
}

object UpdateAlg {
  def isUpdateFor(update: Update, crossDependency: CrossDependency): Boolean =
    crossDependency.dependencies.forall { dependency =>
      update.groupId === dependency.groupId &&
      update.currentVersion === dependency.version &&
      update.artifactIds.contains_(dependency.artifactId)
    }

  def findUpdateWithNewerGroupId(dependency: Dependency): Option[Update.Single] =
    newerGroupId(dependency.groupId, dependency.artifactId).map {
      case (groupId, version) =>
        Update.Single(CrossDependency(dependency), Nel.one(version), Some(groupId))
    }

  private def newerGroupId(groupId: GroupId, artifactId: ArtifactId): Option[(GroupId, String)] =
    Some((groupId.value, artifactId.name)).collect {
      case ("com.geirsson", "sbt-scalafmt")       => (GroupId("org.scalameta"), "2.0.0")
      case ("com.github.mpilquist", "simulacrum") => (GroupId("org.typelevel"), "1.0.0")
      case ("net.ceedubs", "ficus")               => (GroupId("com.iheart"), "1.3.4")
      case ("org.spire-math", "kind-projector")   => (GroupId("org.typelevel"), "0.10.0")
    }

  /** Extracts dependency groups where each dependency in a group has
    * the same groupId and version but different updates or no update
    * at all.
    *
    * This is not unexpected since we're using a cache for dependency
    * versions and not all artifacts of a dependency group are refreshed
    * at the same time.
    */
  def extractOutOfSyncDependencies(
      dependencies: List[Scope.Dependency],
      updates: List[Update.Single]
  ): (List[Scope.Dependency], List[Update.Single]) = {
    val outdatedGroupIdVersionPairs = updates.map(u => (u.groupId, u.currentVersion))
    val matchingDependencies = dependencies.filter { d =>
      outdatedGroupIdVersionPairs.contains_((d.value.groupId, d.value.version))
    }
    val outOfSyncDependencies = matchingDependencies
      .groupBy(d => (d.value.groupId, d.value.version))
      .values
      .filterNot(_.size === 1)
      .filterNot { ds =>
        val matchingUpdates = ds.mapFilter(d => updates.find(_.crossDependency.head === d.value))
        val uniqueNextVersion = matchingUpdates.map(_.nextVersion).distinct.size === 1
        (matchingUpdates.size === ds.size) && uniqueNextVersion
      }
      .toList
      .flatten

    val inSyncUpdates =
      updates.filterNot(u => outOfSyncDependencies.exists(_.value === u.crossDependency.head))

    (outOfSyncDependencies, inSyncUpdates)
  }
}
