/*
 * Copyright 2018-2019 Scala Steward contributors
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
import org.scalasteward.core.data.{GroupId, Update, Version}
import org.scalasteward.core.util.Nel

package object scalafix {
  val migrations: List[Migration] =
    List(
      Migration(
        GroupId("co.fs2"),
        Nel.of("fs2-.*".r),
        Version("1.0.0"),
        Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5")
      ),
      Migration(
        GroupId("com.spotify"),
        Nel.of("scio-.*".r),
        Version("0.7.0"),
        Nel.of(
          "github:spotify/scio/FixAvroIO?sha=v0.7.4",
          "github:spotify/scio/AddMissingImports?sha=v0.7.4",
          "github:spotify/scio/RewriteSysProp?sha=v0.7.4",
          "github:spotify/scio/BQClientRefactoring?sha=v0.7.4"
        )
      ),
      Migration(
        GroupId("org.http4s"),
        Nel.of("http4s-.*".r),
        Version("0.20.0"),
        Nel.of("github:http4s/http4s/v0_20?sha=v0.20.11")
      ),
      Migration(
        GroupId("org.typelevel"),
        Nel.of("cats-core".r),
        Version("1.0.0"),
        Nel.of(
          "https://raw.githubusercontent.com/typelevel/cats/master/scalafix/rules/src/main/scala/fix/Cats_v1_0_0.scala"
        )
      ),
      Migration(
        GroupId("org.scalatest"),
        Nel.of("scalatest".r),
        Version("3.1.0"),
        Nel.of(
          "https://raw.githubusercontent.com/scalatest/autofix/e4de53fa40fac423bd64d165ff36bde38ce52388/3.0.x/rules/src/main/scala/org/scalatest/autofix/v3_0_x/RenameDeprecatedPackage.scala",
          "https://raw.githubusercontent.com/scalatest/autofix/e4de53fa40fac423bd64d165ff36bde38ce52388/3.1.x/rules/src/main/scala/org/scalatest/autofix/v3_1_x/RewriteDeprecatedNames.scala"
        )
      ),
      Migration(
        GroupId("org.scalacheck"),
        Nel.of("scalacheck".r),
        Version("1.14.1"),
        Nel.of("github:typelevel/scalacheck/v1_14_1?sha=3fc537dde9d8fdf951503a8d8b027a568d52d055")
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
