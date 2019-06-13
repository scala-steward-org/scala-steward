/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core

import cats.implicits._
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel

package object scalafix {
  val migrations: List[Migration] =
    List(
      Migration(
        "co.fs2",
        Nel.one("fs2-core"),
        "0.10.0",
        "1.0.0",
        "github:amarrella/fs2/v1?sha=672ea4f9"
      )
    )

  def findMigrations(update: Update): List[Migration] =
    migrations.filter { migration =>
      update.groupId === migration.groupId &&
      util.intersects(update.artifactIds, migration.artifactIds) &&
      update.newerVersions.head === migration.newVersion
    }
}
