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
import org.scalasteward.core.data.NoLongerNeeded.{AlreadyApplied, DependencyRemoved}
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

  test("Single.noLongerNeededIn: base branch has the proposed version or newer ones") {
    val update = ("org.typelevel".g % "cats-core".a % "1.0.0" %> "1.0.1").single
    val proposed = "org.typelevel".g % "cats-core".a % "1.0.1"
    val newer = "org.typelevel".g % "cats-core".a % "1.2.0"
    assertEquals(
      update.noLongerNeededIn(List(proposed), Nil),
      Some(AlreadyApplied(Nel.one(proposed)))
    )
    assertEquals(
      update.noLongerNeededIn(List(newer, proposed), Nil),
      Some(AlreadyApplied(Nel.of(newer, proposed)))
    )
  }

  test("Single.noLongerNeededIn: base branch still has the current version somewhere") {
    val update = ("org.typelevel".g % "cats-core".a % "1.0.0" %> "1.0.1").single
    val current = "org.typelevel".g % "cats-core".a % "1.0.0"
    val newer = "org.typelevel".g % "cats-core".a % "1.0.1"
    assertEquals(update.noLongerNeededIn(List(current), Nil), None)
    assertEquals(update.noLongerNeededIn(List(current, newer), Nil), None)
  }

  test("Single.noLongerNeededIn: occurrences older than the current version do not count") {
    // e.g. the Scala version of the sbt meta-build next to the one of the build
    val update = ("org.scala-lang".g % "scala-library".a % "2.13.15" %> "2.13.16").single
    val metaBuild = "org.scala-lang".g % "scala-library".a % "2.12.20"
    val build = "org.scala-lang".g % "scala-library".a % "2.13.16"
    assertEquals(
      update.noLongerNeededIn(List(metaBuild, build), Nil),
      Some(AlreadyApplied(Nel.one(build)))
    )
    // only older occurrences left: neither applied nor removed
    assertEquals(update.noLongerNeededIn(List(metaBuild), Nil), None)
  }

  test(
    "Single.noLongerNeededIn: an older occurrence counts if an update to the same version is pending"
  ) {
    // foo_2.13 at 1.10 and foo_3 at 1.2; the PR 1.10 -> 1.11 shares its branch with foo_3's
    // 1.2 -> 1.11, which Steward only proposes because the cross names differ (otherwise the
    // "no overtaking updates" rule would drop it)
    val update = ("com.example".g % ("foo", "foo_2.13").a % "1.10" %> "1.11").single
    val a = "com.example".g % ("foo", "foo_2.13").a % "1.11"
    val b = "com.example".g % ("foo", "foo_3").a % "1.2"
    val bToSameVersion = ("com.example".g % ("foo", "foo_3").a % "1.2" %> "1.11").single
    val bToNewerVersion = ("com.example".g % ("foo", "foo_3").a % "1.2" %> "1.12").single
    val otherArtifact = ("com.example".g % "bar".a % "1.2" %> "1.11").single

    assertEquals(update.noLongerNeededIn(List(a, b), List(bToSameVersion)), None)
    assertEquals(
      update.noLongerNeededIn(List(a, b), List(bToNewerVersion, otherArtifact)),
      Some(AlreadyApplied(Nel.one(a)))
    )
    // no pending update for the older occurrence, e.g. pinned or overtaken: it does not count
    assertEquals(update.noLongerNeededIn(List(a, b), Nil), Some(AlreadyApplied(Nel.one(a))))
  }

  test("Single.noLongerNeededIn: matches cross-built artifacts by name") {
    val update =
      ("org.typelevel".g % ("cats-core", "cats-core_2.13").a % "1.0.0" %> "1.0.1").single
    val dependency = "org.typelevel".g % ("cats-core", "cats-core_3").a % "1.0.1"
    assertEquals(
      update.noLongerNeededIn(List(dependency), Nil),
      Some(AlreadyApplied(Nel.one(dependency)))
    )
  }

  test("Single.noLongerNeededIn: dependency no longer present") {
    val update = ("org.typelevel".g % "cats-core".a % "1.0.0" %> "1.0.1").single
    val otherArtifact = "org.typelevel".g % "cats-effect".a % "3.0.0"
    val otherGroup = "com.example".g % "cats-core".a % "2.0.0"
    assertEquals(update.noLongerNeededIn(List.empty, Nil), Some(DependencyRemoved))
    assertEquals(
      update.noLongerNeededIn(List(otherArtifact, otherGroup), Nil),
      Some(DependencyRemoved)
    )
  }

  test("Single.noLongerNeededIn: group update needs all remaining artifacts to be updated") {
    val update = ("org.specs2".g % Nel.of("specs2-core".a, "specs2-scalacheck".a) %
      "3.9.4" %> "3.9.5").group
    val core = "org.specs2".g % "specs2-core".a % "3.9.5"
    val scalacheckOld = "org.specs2".g % "specs2-scalacheck".a % "3.9.4"
    val scalacheckNew = "org.specs2".g % "specs2-scalacheck".a % "3.9.5"
    assertEquals(update.noLongerNeededIn(List(core, scalacheckOld), Nil), None)
    assertEquals(
      update.noLongerNeededIn(List(core, scalacheckNew), Nil),
      Some(AlreadyApplied(Nel.of(core, scalacheckNew)))
    )
    // one artifact removed, the other one updated
    assertEquals(update.noLongerNeededIn(List(core), Nil), Some(AlreadyApplied(Nel.one(core))))
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
