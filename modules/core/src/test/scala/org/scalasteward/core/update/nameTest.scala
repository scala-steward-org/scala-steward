package org.scalasteward.core.update

import org.scalasteward.core.model.Update.{Group, Single}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class nameTest extends FunSuite with Matchers {

  test("oneLiner: cats-core") {
    val update = Single("org.typelevel", "cats-core", "0.9.0", Nel.one("1.0.0"))
    name.oneLiner(update) shouldBe "cats-core"
  }

  test("oneLiner: fs2-core") {
    val update = Single("co.fs2", "fs2-core", "0.9.7", Nel.one("1.0.0"))
    name.oneLiner(update) shouldBe "fs2-core"
  }

  test("oneLiner: monix") {
    val update = Single("io.monix", "monix", "2.3.3", Nel.one("3.0.0"))
    name.oneLiner(update) shouldBe "monix"
  }

  test("oneLiner: sttp:core") {
    val update = Single("com.softwaremill.sttp", "core", "1.3.3", Nel.one("1.3.5"))
    name.oneLiner(update) shouldBe "sttp:core"
  }

  test("oneLiner: typesafe:config") {
    val update = Single("com.typesafe", "config", "1.3.0", Nel.one("1.3.3"))
    name.oneLiner(update) shouldBe "typesafe:config"
  }

  test("oneLiner: single update with very long artifactId") {
    val artifactId = "1234567890" * 5
    val update = Single("org.example", artifactId, "1.0.0", Nel.one("2.0.0"))
    name.oneLiner(update) shouldBe artifactId
  }

  test("oneLiner: group update with two artifacts") {
    val update = Group("org.typelevel", Nel.of("cats-core", "cats-free"), "0.9.0", Nel.one("1.0.0"))
    val expected = "cats-core, cats-free"
    name.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update with four artifacts") {
    val update = Group(
      "org.typelevel",
      Nel.of("cats-core", "cats-free", "cats-laws", "cats-macros"),
      "0.9.0",
      Nel.one("1.0.0")
    )
    val expected = "cats-core, cats-free, cats-laws, ..."
    name.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update with four short artifacts") {
    val update = Group("group", Nel.of("data", "free", "laws", "macros"), "0.9.0", Nel.one("1.0.0"))
    val expected = "data, free, laws, macros"
    name.oneLiner(update) shouldBe expected
  }

  test("oneLiner: group update where one artifactId is a common suffix") {
    val update = Group(
      "com.softwaremill.sttp",
      Nel.of("circe", "core", "okhttp-backend-monix"),
      "1.3.3",
      Nel.one("1.3.5")
    )
    val expected = "sttp:circe, sttp:core, ..."
    name.oneLiner(update) shouldBe expected
  }
}
