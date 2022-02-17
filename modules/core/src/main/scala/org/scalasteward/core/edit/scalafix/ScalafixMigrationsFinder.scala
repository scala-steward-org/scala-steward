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

package org.scalasteward.core.edit.scalafix

import cats.syntax.all._
import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.scalafix.ScalafixMigration.ExecutionOrder

final class ScalafixMigrationsFinder(migrations: List[ScalafixMigration]) {
  def findMigrations(update: Update): (List[ScalafixMigration], List[ScalafixMigration]) =
    migrations
      .filter { migration =>
        update.groupId === migration.groupId &&
        migration.artifactIds.exists { re =>
          update.artifactIds.exists(artifactId => re.r.findFirstIn(artifactId.name).isDefined)
        } &&
        update.currentVersion < migration.newVersion &&
        update.nextVersion >= migration.newVersion
      }
      .partition(_.executionOrderOrDefault === ExecutionOrder.PreUpdate)
}
