package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.ArtifactId
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class showTest extends AnyFunSuite with Matchers {
  test("oneLiner: cats-core") {
    val update = Single(
      "org.typelevel" % Nel.of(
        ArtifactId("cats-core", "cats-core_2.11"),
        ArtifactId("cats-core", "cats-core_2.12")
      ) % "0.9.0",
      Nel.one("1.0.0")
    )
    show.oneLiner(update) shouldBe "cats-core"
  }

  test("oneLiner: fs2-core") {
    val update = Single("co.fs2" % "fs2-core" % "0.9.7", Nel.one("1.0.0"))
    show.oneLiner(update) shouldBe "fs2-core"
  }

  test("oneLiner: monix") {
    val update = Single("io.monix" % "monix" % "2.3.3", Nel.one("3.0.0"))
    show.oneLiner(update) shouldBe "monix"
  }

  test("oneLiner: sttp:core") {
    val update = Single("com.softwaremill.sttp" % "core" % "1.3.3", Nel.one("1.3.5"))
    show.oneLiner(update) shouldBe "sttp:core"
  }

  test("oneLiner: typesafe:config") {
    val update = Single("com.typesafe" % "config" % "1.3.0", Nel.one("1.3.3"))
    show.oneLiner(update) shouldBe "typesafe:config"
  }

  test("oneLiner: single update with very long artifactId") {
    val artifactId = "1234567890" * 5
    val update = Single("org.example" % artifactId % "1.0.0", Nel.one("2.0.0"))
    show.oneLiner(update) shouldBe artifactId
  }

  test("oneLiner: group update with two artifacts") {
    val update =
      Group(
        "org.typelevel" % Nel.of("cats-core", "cats-free") % "0.9.0",
        Nel.one("1.0.0")
      )
    val expected = "cats-core, cats-free"
    show.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update with four artifacts") {
    val update = Group(
      "org.typelevel" % Nel.of("cats-core", "cats-free", "cats-laws", "cats-macros") % "0.9.0",
      Nel.one("1.0.0")
    )
    val expected = "cats-core, cats-free, cats-laws, ..."
    show.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update with four short artifacts") {
    val update =
      Group("group" % Nel.of("data", "free", "laws", "macros") % "0.9.0", Nel.one("1.0.0"))
    val expected = "data, free, laws, macros"
    show.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update where one artifactId is a common suffix") {
    val update = Group(
      "com.softwaremill.sttp" % Nel.of("circe", "core", "okhttp-backend-monix") % "1.3.3",
      Nel.one("1.3.5")
    )
    val expected = "sttp:circe, sttp:core, ..."
    show.oneLiner(update) shouldBe expected
  }
}
