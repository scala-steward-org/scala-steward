/*
 * Copyright 2018-2022 Scala Steward contributors
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
import org.scalasteward.core.data.Dependency

final class ArtifactMigrationsFinder(migrations: List[ArtifactChange]) {
  def findArtifactChange(dependency: Dependency): Option[ArtifactChange] =
    migrations
      .find { migration =>
        (migration.groupIdBefore, migration.artifactIdBefore) match {
          case (Some(groupId), Some(artifactId)) =>
            groupId === dependency.groupId &&
            artifactId === dependency.artifactId.name
          case (Some(groupId), None) =>
            groupId === dependency.groupId &&
            migration.artifactIdAfter === dependency.artifactId.name
          case (None, Some(artifactId)) =>
            migration.groupIdAfter === dependency.groupId &&
            artifactId === dependency.artifactId.name
          case (None, None) => false
        }
      }
}
