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

package org.scalasteward.core.data

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.util.Nel

class UpdateTest extends FunSuite {
  test("Group.mainArtifactId") {
    val update = ("org.http4s".g %
      Nel.of("http4s-blaze-server".a, "http4s-circe".a, "http4s-core".a, "http4s-dsl".a) %
      "0.18.16" %> "0.18.18").group
    assertEquals(update.mainArtifactId, "http4s-core")
  }

  test("Group.mainArtifactId: artifactIds contains a common suffix") {
    val update = ("com.softwaremill.sttp".g %
      Nel.of("circe".a, "core".a, "monix".a) % "1.3.2" %> "1.3.3").group
    assertEquals(update.mainArtifactId, "circe")
  }

  test("groupByGroupId: 1 update") {
    val updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single)
    assertEquals(Update.groupByGroupId(updates), updates)
  }

  test("groupByGroupId: 2 updates") {
    val update0 = ("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single
    val update1 = ("org.specs2".g % "specs2-scalacheck".a % "3.9.4" %> "3.9.5").single
    val expected = List(
      ("org.specs2".g % Nel.of("specs2-core".a, "specs2-scalacheck".a) % "3.9.4" %> "3.9.5").group
    )
    assertEquals(Update.groupByGroupId(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 2 updates") {
    val update0 =
      ("org.specs2".g % ("specs2-core", "specs2-core_2.12").a % "3.9.4" %> "3.9.5").single
    val update1 =
      ("org.specs2".g % ("specs2-core", "specs2-core_2.13").a % "3.9.4" % "test" %> "3.9.5").single
    val expected = List(
      ((update0.crossDependency.dependencies ::: update1.crossDependency.dependencies) -> "3.9.5").single
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 3 updates ") {
    val update0 = ("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single
    val update1 = ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    val update2 = ("org.specs2".g % "specs2-scalacheck".a % "3.9.4" %> "3.9.5").single
    val expected = List(
      (Nel.of(
        "org.specs2".g % "specs2-core".a % "3.9.4",
        "org.specs2".g % "specs2-core".a % "3.9.4" % "test"
      ) %> "3.9.5").single,
      update2
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1, update2)), expected)
  }

  test("Update.show") {
    val update = ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    assertEquals(update.show, "org.specs2:specs2-core : 3.9.4 -> 3.9.5")
  }

  test("Group.show") {
    val update =
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    assertEquals(update.show, "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4")
  }
}
