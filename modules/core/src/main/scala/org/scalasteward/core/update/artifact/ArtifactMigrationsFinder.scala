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

package org.scalasteward.core.update.artifact

import cats.syntax.all._
import org.scalasteward.core.data.{CrossDependency, Dependency, Update}
import org.scalasteward.core.util.Nel

final class ArtifactMigrationsFinder(migrations: List[ArtifactChange]) {
  def findUpdateWithRenamedArtifact(dependency: Dependency): Option[Update.Single] =
    migrations
      .find { migration =>
        migration.before match {
          case ArtifactBefore.Full(groupId, artifactId) =>
            groupId === dependency.groupId &&
              artifactId === dependency.artifactId.name
          case ArtifactBefore.GroupIdOnly(groupId) =>
            groupId === dependency.groupId &&
              migration.artifactIdAfter === dependency.artifactId.name
          case ArtifactBefore.ArtifactIdOnly(artifactId) =>
            migration.groupIdAfter === dependency.groupId &&
              artifactId === dependency.artifactId.name
        }
      }
      .map { migration =>
        Update.Single(
          CrossDependency(dependency),
          Nel.one(migration.initialVersion),
          Some(migration.groupIdAfter),
          Some(migration.artifactIdAfter)
        )
      }
}
