package eu.timepit.scruffy

import cats.data.{NonEmptyList => Nel}
import org.scalatest.{FunSuite, Matchers}

class DependencyUpdateTest extends FunSuite with Matchers {
  test("1 update") {
    DependencyUpdate.fromString("org.scala-js:sbt-scalajs : 0.6.24 -> 0.6.25") shouldBe Right(
      DependencyUpdate("org.scala-js", "sbt-scalajs", "0.6.24", Nel.one("0.6.25")))
  }

  test("2 updates") {
    DependencyUpdate.fromString("org.scala-lang:scala-library   : 2.9.1 -> 2.9.3 -> 2.10.3") shouldBe Right(
      DependencyUpdate("org.scala-lang", "scala-library", "2.9.1", Nel.of("2.9.3", "2.10.3")))
  }

  test("3 updates") {
    DependencyUpdate.fromString(
      "ch.qos.logback:logback-classic : 0.8   -> 0.8.1 -> 0.9.30 -> 1.0.13") shouldBe Right(
      DependencyUpdate(
        "ch.qos.logback",
        "logback-classic",
        "0.8",
        Nel.of("0.8.1", "0.9.30", "1.0.13")))
  }

  test("no groupId") {
    DependencyUpdate.fromString(":sbt-scalajs : 0.6.24 -> 0.6.25").isLeft
  }

  test("no version") {
    DependencyUpdate
      .fromString("ch.qos.logback:logback-classic :  -> 0.8.1 -> 0.9.30 -> 1.0.13")
      .isLeft
  }

  test("no updates") {
    DependencyUpdate.fromString("ch.qos.logback:logback-classic : 0.8 ->").isLeft
  }
}
