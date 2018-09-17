package eu.timepit.scalasteward.model

import cats.data.{NonEmptyList => Nel}
import org.scalatest.{FunSuite, Matchers}

class UpdateTest extends FunSuite with Matchers {

  test("fromString: 1 update") {
    val str = "org.scala-js:sbt-scalajs : 0.6.24 -> 0.6.25"
    Update.fromString(str) shouldBe Right(
      Update.Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25"))
    )
  }

  test("fromString: 2 updates") {
    val str = "org.scala-lang:scala-library   : 2.9.1 -> 2.9.3 -> 2.10.3"
    Update.fromString(str) shouldBe Right(
      Update.Single("org.scala-lang", "scala-library", "2.9.1", Nel.of("2.9.3", "2.10.3"))
    )
  }

  test("fromString: 3 updates") {
    val str = "ch.qos.logback:logback-classic : 0.8   -> 0.8.1 -> 0.9.30 -> 1.0.13"
    Update.fromString(str) shouldBe Right(
      Update.Single(
        "ch.qos.logback",
        "logback-classic",
        "0.8",
        Nel.of("0.8.1", "0.9.30", "1.0.13")
      )
    )
  }

  test("fromString: test dependency") {
    val str = "org.scalacheck:scalacheck:test   : 1.12.5 -> 1.12.6  -> 1.14.0"
    Update.fromString(str) shouldBe Right(
      Update.Single("org.scalacheck", "scalacheck", "1.12.5", Nel.of("1.12.6", "1.14.0"))
    )
  }

  test("fromString: no groupId") {
    val str = ":sbt-scalajs : 0.6.24 -> 0.6.25"
    Update.fromString(str).isLeft
  }

  test("fromString: no version") {
    val str = "ch.qos.logback:logback-classic :  -> 0.8.1 -> 0.9.30 -> 1.0.13"
    Update.fromString(str).isLeft
  }

  test("fromString: no updates") {
    val str = "ch.qos.logback:logback-classic : 0.8 ->"
    Update.fromString(str).isLeft
  }

  test("replaceAllIn: updated") {
    val original =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    val expected =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Update
      .Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: not updated") {
    val orig =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Update
      .Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25"))
      .replaceAllIn(orig) shouldBe None
  }

  test("replaceAllIn: ignore hyphen") {
    val original = """val scalajsJqueryVersion = "0.9.3""""
    val expected = """val scalajsJqueryVersion = "0.9.4""""
    Update
      .Single("be.doeraene", "scalajs-jquery", "0.9.3", Nel.of("0.9.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: ignore '-core' suffix") {
    val original = """val specs2Version = "4.2.0""""
    val expected = """val specs2Version = "4.3.4""""
    Update
      .Single("org.specs2", "specs2-core", "4.2.0", Nel.of("4.3.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: use groupId if artifactId is 'core'") {
    val original = """lazy val sttpVersion = "1.3.2""""
    val expected = """lazy val sttpVersion = "1.3.3""""
    Update
      .Single("com.softwaremill.sttp", "core", "1.3.2", Nel.of("1.3.3"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: version range") {
    val original = """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")"""
    val expected = """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")"""
    Update
      .Single("org.specs2", "specs2-core", "3.+", Nel.of("4.3.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("lockstep") {
    val original = """ val circe = "0.10.0-M1" """
    val expected = """ val circe = "0.10.0-M2" """
    Update
      .Group(Nel.of(
        Update.Single("io.circe", "circe-generic", "0.10.0-M1", Nel.of("0.10.0-M2")),
        Update.Single("io.circe", "circe-literal", "0.10.0-M1", Nel.of("0.10.0-M2")),
        Update.Single("io.circe", "circe-parser", "0.10.0-M1", Nel.of("0.10.0-M2")),
        Update.Single(" io.circe", "circe-testing", "0.10.0-M1", Nel.of("0.10.0-M2"))
      ))
      .replaceAllIn(original) shouldBe Some(expected)
  }
}
