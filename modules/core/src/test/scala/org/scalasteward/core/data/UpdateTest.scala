package org.scalasteward.core.data

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateTest extends AnyFunSuite with Matchers {
  test("Group.mainArtifactId") {
    Group(
      "org.http4s" %
        Nel.of("http4s-blaze-server", "http4s-circe", "http4s-core", "http4s-dsl") % "0.18.16",
      Nel.of("0.18.18")
    ).mainArtifactId shouldBe "http4s-core"
  }

  test("Group.mainArtifactId: artifactIds contains a common suffix") {
    val update =
      Group("com.softwaremill.sttp" % Nel.of("circe", "core", "monix") % "1.3.2", Nel.of("1.3.3"))
    update.mainArtifactId shouldBe "circe"
  }

  test("groupByGroupId: 1 update") {
    val updates = List(Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5")))
    Update.groupByGroupId(updates) shouldBe updates
  }

  test("groupByGroupId: 2 updates") {
    val update0 = Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5"))
    val update1 = Single("org.specs2" % "specs2-scalacheck" % "3.9.4", Nel.of("3.9.5"))
    Update.groupByGroupId(List(update0, update1)) shouldBe List(
      Group("org.specs2" % Nel.of("specs2-core", "specs2-scalacheck") % "3.9.4", Nel.of("3.9.5"))
    )
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
    Update.groupByArtifactIdName(List(update0, update1)) shouldBe List(
      Single(
        update0.crossDependency.dependencies ::: update1.crossDependency.dependencies,
        Nel.of("3.9.5")
      )
    )
  }

  test("groupByArtifactIdName: 3 updates ") {
    val update0 = Single("org.specs2" % "specs2-core" % "3.9.4", Nel.of("3.9.5"))
    val update1 = Single("org.specs2" % "specs2-core" % "3.9.4" % "test", Nel.of("3.9.5"))
    val update2 = Single("org.specs2" % "specs2-scalacheck" % "3.9.4", Nel.of("3.9.5"))
    Update.groupByArtifactIdName(List(update0, update1, update2)) shouldBe List(
      Single(
        Nel.of(
          "org.specs2" % "specs2-core" % "3.9.4",
          "org.specs2" % "specs2-core" % "3.9.4" % "test"
        ),
        Nel.of("3.9.5")
      ),
      update2
    )
  }

  test("Single.show") {
    val update = Single("org.specs2" % "specs2-core" % "3.9.4" % "test", Nel.of("3.9.5"))
    update.show shouldBe "org.specs2:specs2-core : 3.9.4 -> 3.9.5"
  }

  test("Group.show") {
    val update =
      Group("org.scala-sbt" % Nel.of("sbt-launch", "scripted-plugin") % "1.2.1", Nel.of("1.2.4"))
    update.show shouldBe "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4"
  }
}
