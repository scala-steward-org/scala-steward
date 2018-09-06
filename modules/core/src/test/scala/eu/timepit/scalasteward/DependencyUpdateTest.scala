package eu.timepit.scalasteward

import cats.data.{NonEmptyList => Nel}
import org.scalatest.{FunSuite, Matchers}

class DependencyUpdateTest extends FunSuite with Matchers {

  test("fromString: 1 update") {
    val str = "org.scala-js:sbt-scalajs : 0.6.24 -> 0.6.25"
    DependencyUpdate.fromString(str) shouldBe Right(
      DependencyUpdate("org.scala-js", "sbt-scalajs", "0.6.24", Nel.one("0.6.25"))
    )
  }

  test("fromString: 2 updates") {
    val str = "org.scala-lang:scala-library   : 2.9.1 -> 2.9.3 -> 2.10.3"
    DependencyUpdate.fromString(str) shouldBe Right(
      DependencyUpdate("org.scala-lang", "scala-library", "2.9.1", Nel.of("2.9.3", "2.10.3"))
    )
  }

  test("fromString: 3 updates") {
    val str = "ch.qos.logback:logback-classic : 0.8   -> 0.8.1 -> 0.9.30 -> 1.0.13"
    DependencyUpdate.fromString(str) shouldBe Right(
      DependencyUpdate(
        "ch.qos.logback",
        "logback-classic",
        "0.8",
        Nel.of("0.8.1", "0.9.30", "1.0.13")
      )
    )
  }

  test("fromString: no groupId") {
    val str = ":sbt-scalajs : 0.6.24 -> 0.6.25"
    DependencyUpdate.fromString(str).isLeft
  }

  test("fromString: no version") {
    val str = "ch.qos.logback:logback-classic :  -> 0.8.1 -> 0.9.30 -> 1.0.13"
    DependencyUpdate.fromString(str).isLeft
  }

  test("fromString: no updates") {
    val str = "ch.qos.logback:logback-classic : 0.8 ->"
    DependencyUpdate.fromString(str).isLeft
  }

  test("replaceAllIn: updated") {
    val orig =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    val expected =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    DependencyUpdate("org.scala-js", "sbt-scalajs", "0.6.24", Nel.one("0.6.25"))
      .replaceAllIn(orig) shouldBe Some(expected)
  }

  test("replaceAllIn: not updated") {
    val orig =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    DependencyUpdate("org.scala-js", "sbt-scalajs", "0.6.24", Nel.one("0.6.25"))
      .replaceAllIn(orig) shouldBe None
  }
}
