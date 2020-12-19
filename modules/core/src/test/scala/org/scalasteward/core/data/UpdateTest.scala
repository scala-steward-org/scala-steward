package org.scalasteward.core.data

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util.Nel

class UpdateTest extends FunSuite {
  test("Group.mainArtifactId") {
    val update = Group(
      "org.http4s" %
        Nel.of("http4s-blaze-server", "http4s-circe", "http4s-core", "http4s-dsl") % "0.18.16",
      Nel.of("0.18.18")
    )
    assertEquals(update.mainArtifactId, "http4s-core")
  }

  test("Group.mainArtifactId: artifactIds contains a common suffix") {
    val update =
      Group("com.softwaremill.sttp" % Nel.of("circe", "core", "monix") % "1.3.2", Nel.of("1.3.3"))
    assertEquals(update.mainArtifactId, "circe")
  }

  test("groupByGroupId: 1 update") {
    val updates = List(Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5")))
    assertEquals(Update.groupByGroupId(updates), updates)
  }

  test("groupByGroupId: 2 updates") {
    val update0 = Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5"))
    val update1 = Single("org.specs2" % "specs2-scalacheck" % "3.9.4", Nel.of("3.9.5"))
    val expected = List(
      Group("org.specs2" % Nel.of("specs2-core", "specs2-scalacheck") % "3.9.4", Nel.of("3.9.5"))
    )
    assertEquals(Update.groupByGroupId(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 2 updates") {
    val update0 = Single(
      "org.specs2" % ArtifactId("specs2-core", "specs2-core_2.12") % "3.9.4",
      Nel.of("3.9.5")
    )
    val update1 = Single(
      "org.specs2" % ArtifactId("specs2-core", "specs2-core_2.13") % "3.9.4" % "test",
      Nel.of("3.9.5")
    )
    val expected = List(
      Single(
        update0.crossDependency.dependencies ::: update1.crossDependency.dependencies,
        Nel.of("3.9.5")
      )
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 3 updates ") {
    val update0 = Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5"))
    val update1 = Single("org.specs2" % "specs2-core" % "3.9.4" % "test", Nel.of("3.9.5"))
    val update2 = Single("org.specs2" % "specs2-scalacheck" % "3.9.4", Nel.of("3.9.5"))
    val expected = List(
      Single(
        Nel.of(
          "org.specs2" % "specs2-core" % "3.9.4",
          "org.specs2" % "specs2-core" % "3.9.4" % "test"
        ),
        Nel.of("3.9.5")
      ),
      update2
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1, update2)), expected)
  }

  test("Single.show") {
    val update = Single("org.specs2" % "specs2-core" % "3.9.4" % "test", Nel.of("3.9.5"))
    assertEquals(update.show, "org.specs2:specs2-core : 3.9.4 -> 3.9.5")
  }

  test("Group.show") {
    val update =
      Group("org.scala-sbt" % Nel.of("sbt-launch", "scripted-plugin") % "1.2.1", Nel.of("1.2.4"))
    assertEquals(update.show, "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4")
  }
}
