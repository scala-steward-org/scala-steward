package org.scalasteward.core.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.util.Nel

class showTest extends FunSuite {
  test("oneLiner: cats-core") {
    val update = ("org.typelevel".g %
      Nel.of(("cats-core", "cats-core_2.11").a, ("cats-core", "cats-core_2.12").a) %
      "0.9.0" %> "1.0.0").single
    assertEquals(show.oneLiner(update), "cats-core")
  }

  test("oneLiner: fs2-core") {
    val update = ("co.fs2".g % "fs2-core".a % "0.9.7" %> "1.0.0").single
    assertEquals(show.oneLiner(update), "fs2-core")
  }

  test("oneLiner: monix") {
    val update = ("io.monix".g % "monix".a % "2.3.3" %> "3.0.0").single
    assertEquals(show.oneLiner(update), "monix")
  }

  test("oneLiner: sttp:core") {
    val update = ("com.softwaremill.sttp".g % "core".a % "1.3.3" %> "1.3.5").single
    assertEquals(show.oneLiner(update), "sttp:core")
  }

  test("oneLiner: typesafe:config") {
    val update = ("com.typesafe".g % "config".a % "1.3.0" %> "1.3.3").single
    assertEquals(show.oneLiner(update), "typesafe:config")
  }

  test("oneLiner: single update with very long artifactId") {
    val artifactId = "1234567890" * 5
    val update = ("org.example".g % artifactId.a % "1.0.0" %> "2.0.0").single
    assertEquals(show.oneLiner(update), artifactId)
  }

  test("oneLiner: group update with two artifacts") {
    val update =
      ("org.typelevel".g % Nel.of("cats-core".a, "cats-free".a) % "0.9.0" %> "1.0.0").group
    val expected = "cats-core, cats-free"
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update with four artifacts") {
    val update = ("org.typelevel".g %
      Nel.of("cats-core".a, "cats-free".a, "cats-laws".a, "cats-macros".a) %
      "0.9.0" %> "1.0.0").group
    val expected = "cats-core, cats-free, cats-laws, ..."
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update with four short artifacts") {
    val update =
      ("group".g % Nel.of("data".a, "free".a, "laws".a, "macros".a) % "0.9.0" %> "1.0.0").group
    val expected = "data, free, laws, macros"
    assertEquals(show.oneLiner(update), expected)
  }

  test("oneLiner: group update where one artifactId is a common suffix") {
    val update = ("com.softwaremill.sttp".g %
      Nel.of("circe".a, "core".a, "okhttp-backend-monix".a) % "1.3.3" %> "1.3.5").group
    val expected = "sttp:circe, sttp:core, ..."
    assertEquals(show.oneLiner(update), expected)
  }
}
