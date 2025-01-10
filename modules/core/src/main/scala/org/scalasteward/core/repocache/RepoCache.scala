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

package org.scalasteward.core.repocache

import cats.syntax.all.*
import io.circe.Codec
import io.circe.generic.semiauto.*
import org.scalasteward.core.data.{ArtifactId, DependencyInfo, GroupId, Scope}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.repoconfig.RepoConfig

final case class RepoCache(
    sha1: Sha1,
    dependencyInfos: List[Scope[List[DependencyInfo]]],
    maybeRepoConfig: Option[RepoConfig],
    maybeRepoConfigParsingError: Option[String]
) {
  def dependsOn(modules: List[(GroupId, ArtifactId)]): Boolean =
    dependencyInfos.exists(_.value.exists { info =>
      modules.exists { case (groupId, artifactId) =>
        info.dependency.groupId === groupId &&
        info.dependency.artifactId.name === artifactId.name
      }
    })
}

object RepoCache {
  implicit val repoCacheCodec: Codec[RepoCache] =
    deriveCodec
}
