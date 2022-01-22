/*
 * Copyright 2018-2021 Scala Steward contributors
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
import cats.syntax.all._
import org.scalasteward.core.coursier.VersionsCache
import org.scalasteward.core.data._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.UpdateAlg.migratedDependency
import org.scalasteward.core.update.artifact.{ArtifactChange, ArtifactMigrationsFinder}
import org.scalasteward.core.util.Nel

import scala.concurrent.duration.FiniteDuration

final class UpdateAlg[F[_]](implicit
    artifactMigrationsFinder: ArtifactMigrationsFinder,
    filterAlg: FilterAlg[F],
    versionsCache: VersionsCache[F],
    F: Monad[F]
) {
  def findUpdate(
      dependency: Scope[Dependency],
      maxAge: Option[FiniteDuration]
  ): F[Option[Update.Single]] =
    for {
      existingVersions <- versionsCache.getVersions(dependency, maxAge)
      maybeUpdate = maybeNewerVersions(dependency, existingVersions)
        .map(vs => Update.Single(CrossDependency(dependency.value), vs.map(_.value)))
      maybeUpdateOrRename <- maybeUpdate match {
        case Some(update) => F.pure(Some(update))
        case None =>
          artifactMigrationsFinder.findArtifactChange(dependency.value) match {
            case Some(artifactChange) => verifyVersion(dependency, artifactChange, maxAge)
            case None                 => F.pure(None)
          }
      }
    } yield maybeUpdateOrRename

  private def verifyVersion(
      dependency: Scope[Dependency],
      artifactChange: ArtifactChange,
      maxAge: Option[FiniteDuration]
  ): F[Option[Update.Single]] =
    versionsCache
      .getVersions(migratedDependency(dependency, artifactChange), maxAge)
      .map { existingVersions =>
        maybeNewerVersions(dependency, existingVersions)
          .map(newerVersions =>
            Update.Single(
              CrossDependency(dependency.value),
              newerVersions.map(_.value),
              Some(artifactChange.groupIdAfter),
              Some(artifactChange.artifactIdAfter)
            )
          )
      }

  def findUpdates(
      dependencies: List[Scope.Dependency],
      repoConfig: RepoConfig,
      maxAge: Option[FiniteDuration]
  ): F[List[Update.Single]] = {
    val updates = dependencies.traverseFilter(findUpdate(_, maxAge))
    updates.flatMap(filterAlg.localFilterMany(repoConfig, _))
  }

  private def maybeNewerVersions(
      dependency: Scope[Dependency],
      existingVersions: List[Version]
  ): Option[Nel[Version]] = {
    val current = Version(dependency.value.version)
    Nel.fromList(existingVersions.filter(_ > current))
  }
}

object UpdateAlg {
  def isUpdateFor(update: Update, crossDependency: CrossDependency): Boolean =
    crossDependency.dependencies.forall { dependency =>
      update.groupId === dependency.groupId &&
      update.currentVersion === dependency.version &&
      update.artifactIds.contains_(dependency.artifactId)
    }

  def migratedDependency(
      dependency: Scope[Dependency],
      artifactChange: ArtifactChange
  ): Scope[Dependency] = {
    val oldArtifactId = dependency.value.artifactId
    val newGroupId = artifactChange.groupIdAfter
    val newArtifactId = ArtifactId(
      artifactChange.artifactIdAfter,
      oldArtifactId.maybeCrossName.map(
        _.replace(oldArtifactId.name, artifactChange.artifactIdAfter)
      )
    )
    dependency.copy(value =
      dependency.value.copy(
        groupId = newGroupId,
        artifactId = newArtifactId
      )
    )
  }
}
