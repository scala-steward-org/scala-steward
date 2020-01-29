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

import cats.Monad
import cats.implicits._
import org.scalasteward.core.coursier.VersionsCache
import org.scalasteward.core.data._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.UpdateAlg._
import org.scalasteward.core.util.Nel
import scala.concurrent.duration.FiniteDuration

final class UpdateAlg[F[_]](
    implicit
    filterAlg: FilterAlg[F],
    versionsCache: VersionsCache[F],
    F: Monad[F]
) {
  def findUpdate(
      dependency: Scope[Dependency],
      maxAge: Option[FiniteDuration]
  ): F[Option[Update.Single]] =
    for {
      versions <- versionsCache.getVersions(dependency, maxAge)
      current = Version(dependency.value.version)
      maybeNewerVersions = Nel.fromList(versions.filter(_ > current))
      maybeUpdate0 = maybeNewerVersions.map { newerVersions =>
        Update.Single(CrossDependency(dependency.value), newerVersions.map(_.value))
      }
      maybeUpdate1 = maybeUpdate0.orElse(findUpdateWithNewerGroupId(dependency.value))
    } yield maybeUpdate1

  def findUpdates(
      dependencies: List[Scope.Dependency],
      repoConfig: RepoConfig,
      maxAge: Option[FiniteDuration]
  ): F[List[Update.Single]] = {
    val updates = dependencies.traverseFilter(findUpdate(_, maxAge))
    updates.flatMap(filterAlg.localFilterMany(repoConfig, _))
  }
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
}
