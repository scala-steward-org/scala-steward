package org.scalasteward.core.data

import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util.Nel
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite

class UpdateTest extends AnyFunSuite with Matchers {

  test("Group.artifactId") {
    Group(
      "org.http4s",
      Nel.of("http4s-blaze-server", "http4s-circe", "http4s-core", "http4s-dsl"),
      "0.18.16",
      Nel.of("0.18.18")
    ).artifactId shouldBe "http4s-core"
  }

  test("group: 1 update") {
    val updates = List(Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5")))
    Update.group(updates) shouldBe updates
  }

  test("group: 2 updates") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(artifactId = "specs2-scalacheck")
    Update.group(List(update0, update1)) shouldBe List(
      Group("org.specs2", Nel.of("specs2-core", "specs2-scalacheck"), "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("group: 2 updates with different configurations") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(configurations = Some("test"))
    Update.group(List(update0, update1)) shouldBe List(
      Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("group: 3 updates with different configurations") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(configurations = Some("test"))
    val update2 = update0.copy(artifactId = "specs2-scalacheck")
    Update.group(List(update0, update1, update2)) shouldBe List(
      Group("org.specs2", Nel.of("specs2-core", "specs2-scalacheck"), "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("Single.show") {
    val update = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"), Some("test"))
    update.show shouldBe "org.specs2:specs2-core:test : 3.9.4 -> 3.9.5"
  }

  test("Group.show") {
    val update =
      Group("org.scala-sbt", Nel.of("sbt-launch", "scripted-plugin"), "1.2.1", Nel.of("1.2.4"))
    update.show shouldBe "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4"
  }
}
