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
import org.scalasteward.core.model.{Update, Version}
import org.scalasteward.core.util.Nel

package object scalafix {
  val migrations: List[Migration] =
    List(
      Migration(
        "co.fs2",
        Nel.of("fs2-.*".r),
        Version("1.0.0"),
        "github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
      ),
      Migration(
        "org.http4s",
        Nel.of("http4s-.*".r),
        Version("0.20.0"),
        "github:http4s/http4s/v0_20?sha=v0.20.3"
      ),
      Migration(
        "org.typelevel",
        Nel.of("cats-core".r),
        Version("1.0.0"),
        "github:typelevel/cats/Cats_v1_0_0?sha=v1.1.0"
      )
    )

  def findMigrations(update: Update): List[Migration] =
    migrations.filter { migration =>
      update.groupId === migration.groupId &&
      migration.artifactIds.exists(re => update.artifactIds.exists(re.findFirstIn(_).isDefined)) &&
      Version(update.currentVersion) < migration.newVersion &&
      Version(update.newerVersions.head) >= migration.newVersion
    }
}
