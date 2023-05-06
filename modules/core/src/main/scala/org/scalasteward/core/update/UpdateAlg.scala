/*
 * Copyright 2018-2023 Scala Steward contributors
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

import cats.data.OptionT
import cats.syntax.all._
import cats.{Monad, Parallel}
import org.scalasteward.core.coursier.VersionsCache
import org.scalasteward.core.data._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.UpdateAlg.migrateDependency
import org.scalasteward.core.update.artifact.{ArtifactChange, ArtifactMigrationsFinder}
import org.scalasteward.core.util.Nel
import scala.concurrent.duration.FiniteDuration

final class UpdateAlg[F[_]](implicit
    artifactMigrationsFinder: ArtifactMigrationsFinder,
    filterAlg: FilterAlg[F],
    parallel: Parallel[F],
    versionsCache: VersionsCache[F],
    F: Monad[F]
) {
  private def findUpdate(
      dependency: Scope[Dependency],
      repoConfig: RepoConfig,
      maxAge: Option[FiniteDuration]
  ): F[Option[Update.ForArtifactId]] =
    findUpdateWithoutMigration(dependency, maxAge)
      .flatMapF(filterAlg.localFilterSingle(repoConfig, _))
      .orElse(findUpdateWithMigration(dependency, maxAge))
      .flatMapF(filterAlg.localFilterSingle(repoConfig, _))
      .value

  def findUpdates(
      dependencies: List[Scope.Dependency],
      repoConfig: RepoConfig,
      maxAge: Option[FiniteDuration]
  ): F[List[Update.ForArtifactId]] =
    dependencies.parTraverseFilter(findUpdate(_, repoConfig, maxAge))

  private def findUpdateWithoutMigration(
      dependency: Scope[Dependency],
      maxAge: Option[FiniteDuration]
  ): OptionT[F, Update.ForArtifactId] =
    findNewerVersions(dependency, maxAge).map { newerVersions =>
      Update.ForArtifactId(CrossDependency(dependency.value), newerVersions)
    }

  private def findUpdateWithMigration(
      dependency: Scope[Dependency],
      maxAge: Option[FiniteDuration]
  ): OptionT[F, Update.ForArtifactId] =
    OptionT.fromOption(artifactMigrationsFinder.findArtifactChange(dependency.value)).flatMap {
      artifactChange =>
        val migratedDependency = migrateDependency(dependency.value, artifactChange)
        findNewerVersions(dependency.as(migratedDependency), maxAge).map { newerVersions =>
          Update.ForArtifactId(
            CrossDependency(dependency.value),
            newerVersions,
            Some(artifactChange.groupIdAfter),
            Some(artifactChange.artifactIdAfter)
          )
        }
    }

  private def findNewerVersions(
      dependency: Scope[Dependency],
      maxAge: Option[FiniteDuration]
  ): OptionT[F, Nel[Version]] =
    OptionT(versionsCache.getVersions(dependency, maxAge).map { versions =>
      Nel.fromList(versions.filter(_ > dependency.value.version))
    })
}

object UpdateAlg {
  def isUpdateFor(update: Update.Single, crossDependency: CrossDependency): Boolean =
    crossDependency.dependencies.forall { dependency =>
      update.groupId === dependency.groupId &&
      update.currentVersion === dependency.version &&
      update.artifactIds.contains_(dependency.artifactId)
    }

  private def migrateArtifactId(artifactId: ArtifactId, newName: String): ArtifactId =
    ArtifactId(newName, artifactId.maybeCrossName.map(_.replace(artifactId.name, newName)))

  def migrateDependency(dependency: Dependency, change: ArtifactChange): Dependency =
    dependency.copy(
      groupId = change.groupIdAfter,
      artifactId = migrateArtifactId(dependency.artifactId, change.artifactIdAfter)
    )
}
