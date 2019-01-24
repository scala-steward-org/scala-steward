package org.scalasteward.core.sbt

import org.scalasteward.core.model.Update
import org.scalasteward.core.sbt.parser._
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class parserTest extends FunSuite with Matchers {

  test("parseSingleUpdate: 1 new version") {
    val str = "org.scala-js:sbt-scalajs : 0.6.24 -> 0.6.25"
    parseSingleUpdate(str) shouldBe
      Right(Update.Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25")))
  }

  test("parseSingleUpdate: 2 new versions") {
    val str = "org.scala-lang:scala-library   : 2.9.1 -> 2.9.3 -> 2.10.3"
    parseSingleUpdate(str) shouldBe
      Right(Update.Single("org.scala-lang", "scala-library", "2.9.1", Nel.of("2.9.3", "2.10.3")))
  }

  test("parseSingleUpdate: 3 new versions") {
    val str = "ch.qos.logback:logback-classic : 0.8   -> 0.8.1 -> 0.9.30 -> 1.0.13"
    parseSingleUpdate(str) shouldBe
      Right(
        Update
          .Single("ch.qos.logback", "logback-classic", "0.8", Nel.of("0.8.1", "0.9.30", "1.0.13"))
      )
  }

  test("parseSingleUpdate: test dependency") {
    val str = "org.scalacheck:scalacheck:test   : 1.12.5 -> 1.12.6  -> 1.14.0"
    parseSingleUpdate(str) shouldBe
      Right(
        Update
          .Single(
            "org.scalacheck",
            "scalacheck",
            "1.12.5",
            Nel.of("1.12.6", "1.14.0"),
            Some("test")
          )
      )
  }

  test("parseSingleUpdate: no groupId") {
    val str = ":sbt-scalajs : 0.6.24 -> 0.6.25"
    parseSingleUpdate(str).isLeft
  }

  test("parseSingleUpdate: no current version") {
    val str = "ch.qos.logback:logback-classic :  -> 0.8.1 -> 0.9.30 -> 1.0.13"
    parseSingleUpdate(str).isLeft
  }

  test("parseSingleUpdate: no new versions") {
    val str = "ch.qos.logback:logback-classic : 0.8 ->"
    parseSingleUpdate(str).isLeft
  }

  test("parseSingleUpdates 1") {
    val str =
      """[info] Found 3 dependency updates for datapackage
        |[info]   ai.x:diff:test                           : 1.2.0  -> 1.2.1
        |[info]   eu.timepit:refined                       : 0.7.0             -> 0.9.3
        |[info]   com.geirsson:scalafmt-cli_2.11:scalafmt  : 0.3.0  -> 0.3.1   -> 0.6.8  -> 1.5.1
      """.stripMargin.trim
    parseSingleUpdates(str.linesIterator.toList) shouldBe
      List(
        Update.Single("ai.x", "diff", "1.2.0", Nel.of("1.2.1"), Some("test")),
        Update
          .Single(
            "com.geirsson",
            "scalafmt-cli_2.11",
            "0.3.0",
            Nel.of("0.3.1", "0.6.8", "1.5.1"),
            Some("scalafmt")
          ),
        Update.Single("eu.timepit", "refined", "0.7.0", Nel.of("0.9.3"))
      )
  }

  test("parseSingleUpdates: with duplicates") {
    val lines = List(
      "[info] Found 1 dependency update for refined",
      "[info]   org.scala-lang:scala-library : 2.12.3 -> 2.12.6",
      "[info] Found 2 dependency updates for refined-scalacheck",
      "[info]   org.scala-lang:scala-library : 2.12.3 -> 2.12.6",
      "[info]   org.scalacheck:scalacheck    : 1.13.5           -> 1.14.0",
      "[info] Found 2 dependency updates for refined-pureconfig",
      "[info]   com.github.pureconfig:pureconfig : 0.8.0            -> 0.9.2",
      "[info]   org.scala-lang:scala-library     : 2.12.3 -> 2.12.6"
    )
    parseSingleUpdates(lines) shouldBe
      List(
        Update.Single("com.github.pureconfig", "pureconfig", "0.8.0", Nel.of("0.9.2")),
        Update.Single("org.scala-lang", "scala-library", "2.12.3", Nel.of("2.12.6")),
        Update.Single("org.scalacheck", "scalacheck", "1.13.5", Nel.of("1.14.0"))
      )
  }
}
