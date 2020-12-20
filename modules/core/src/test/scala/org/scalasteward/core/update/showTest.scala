package org.scalasteward.core.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.ArtifactId
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util.Nel

class showTest extends FunSuite {
  test("oneLiner: cats-core") {
    val update = Single(
      "org.typelevel" % Nel.of(
        ArtifactId("cats-core", "cats-core_2.11"),
        ArtifactId("cats-core", "cats-core_2.12")
      ) % "0.9.0",
      Nel.one("1.0.0")
    )
    assertEquals(show.oneLiner(update), "cats-core")
  }

  test("oneLiner: fs2-core") {
    val update = Single("co.fs2" % "fs2-core" % "0.9.7", Nel.one("1.0.0"))
    assertEquals(show.oneLiner(update), "fs2-core")
  }

  test("oneLiner: monix") {
    val update = Single("io.monix" % "monix" % "2.3.3", Nel.one("3.0.0"))
    assertEquals(show.oneLiner(update), "monix")
  }

  test("oneLiner: sttp:core") {
    val update = Single("com.softwaremill.sttp" % "core" % "1.3.3", Nel.one("1.3.5"))
    assertEquals(show.oneLiner(update), "sttp:core")
  }

  test("oneLiner: typesafe:config") {
    val update = Single("com.typesafe" % "config" % "1.3.0", Nel.one("1.3.3"))
    assertEquals(show.oneLiner(update), "typesafe:config")
  }

  test("oneLiner: single update with very long artifactId") {
    val artifactId = "1234567890" * 5
    val update = Single("org.example" % artifactId % "1.0.0", Nel.one("2.0.0"))
    assertEquals(show.oneLiner(update), artifactId)
  }

  test("oneLiner: group update with two artifacts") {
    val update =
      Group(
        "org.typelevel" % Nel.of("cats-core", "cats-free") % "0.9.0",
        Nel.one("1.0.0")
      )
    val expected = "cats-core, cats-free"
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update with four artifacts") {
    val update = Group(
      "org.typelevel" % Nel.of("cats-core", "cats-free", "cats-laws", "cats-macros") % "0.9.0",
      Nel.one("1.0.0")
    )
    val expected = "cats-core, cats-free, cats-laws, ..."
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update with four short artifacts") {
    val update =
      Group("group" % Nel.of("data", "free", "laws", "macros") % "0.9.0", Nel.one("1.0.0"))
    val expected = "data, free, laws, macros"
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update where one artifactId is a common suffix") {
    val update = Group(
      "com.softwaremill.sttp" % Nel.of("circe", "core", "okhttp-backend-monix") % "1.3.3",
      Nel.one("1.3.5")
    )
    val expected = "sttp:circe, sttp:core, ..."
    assertEquals(show.oneLiner(update), expected)
  }
}
