package org.scalasteward.core.sbt

import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.sbt.parser._
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class parserTest extends AnyFunSuite with Matchers {
  test("parseBuildProperties: with whitespace") {
    parseBuildProperties("sbt.version = 1.2.8") shouldBe Some(SbtVersion("1.2.8"))
  }

  test("parseSingleUpdate: 1 new version") {
    val str = "org.scala-js:sbt-scalajs : 0.6.24 -> 0.6.25"
    parseSingleUpdate(str) shouldBe
      Right(Update.Single(GroupId("org.scala-js"), "sbt-scalajs", "0.6.24", Nel.of("0.6.25")))
  }

  test("parseSingleUpdate: 2 new versions") {
    val str = "org.scala-lang:scala-library   : 2.9.1 -> 2.9.3 -> 2.10.3"
    parseSingleUpdate(str) shouldBe
      Right(
        Update
          .Single(GroupId("org.scala-lang"), "scala-library", "2.9.1", Nel.of("2.9.3", "2.10.3"))
      )
  }

  test("parseSingleUpdate: 3 new versions") {
    val str = "ch.qos.logback:logback-classic : 0.8   -> 0.8.1 -> 0.9.30 -> 1.0.13"
    parseSingleUpdate(str) shouldBe
      Right(
        Update.Single(
          GroupId("ch.qos.logback"),
          "logback-classic",
          "0.8",
          Nel.of("0.8.1", "0.9.30", "1.0.13")
        )
      )
  }

  test("parseSingleUpdate: test dependency") {
    val str = "org.scalacheck:scalacheck:test   : 1.12.5 -> 1.12.6  -> 1.14.0"
    parseSingleUpdate(str) shouldBe
      Right(
        Update
          .Single(
            GroupId("org.scalacheck"),
            "scalacheck",
            "1.12.5",
            Nel.of("1.12.6", "1.14.0"),
            Some("test")
          )
      )
  }

  test("parseSingleUpdate: no groupId") {
    val str = ":sbt-scalajs : 0.6.24 -> 0.6.25"
    parseSingleUpdate(str) shouldBe Left(s"failed to parse groupId in '$str'")
  }

  test("parseSingleUpdate: no current version") {
    val str = "ch.qos.logback:logback-classic :  -> 0.8.1 -> 0.9.30 -> 1.0.13"
    parseSingleUpdate(str) shouldBe Left(s"failed to parse currentVersion in '$str'")
  }

  test("parseSingleUpdate: no new versions") {
    val str = "ch.qos.logback:logback-classic : 0.8 ->"
    parseSingleUpdate(str) shouldBe Left(s"failed to parse newerVersions in '$str'")
  }

  test("parseSingleUpdate: all new versions are invalid") {
    val str =
      "bigdataoss:gcs-connector : hadoop2-1.9.16 -> InvalidVersion(hadoop3-2.0.0-SNAPSHOT)"
    parseSingleUpdate(str) shouldBe Left(s"failed to parse newerVersions in '$str'")
  }

  test("parseSingleUpdate: one new version is invalid") {
    val str =
      "bigdataoss:gcs-connector : hadoop2-1.9.16 -> InvalidVersion(hadoop3-2.0.0-SNAPSHOT) -> 1.9.4-hadoop3"
    parseSingleUpdate(str) shouldBe Right(
      Update
        .Single(GroupId("bigdataoss"), "gcs-connector", "hadoop2-1.9.16", Nel.of("1.9.4-hadoop3"))
    )
  }

  test("parseSingleUpdate: new version is current version") {
    val str = "org.scalacheck:scalacheck:test : 1.14.0 -> 1.14.0"
    parseSingleUpdate(str) shouldBe Left(s"failed to parse newerVersions in '$str'")
  }

  test("parseSingleUpdates: 3 updates") {
    val str =
      """[info] Found 3 dependency updates for datapackage
        |[info]   ai.x:diff:test                           : 1.2.0  -> 1.2.1
        |[info]   eu.timepit:refined                       : 0.7.0             -> 0.9.3
        |[info]   com.geirsson:scalafmt-cli_2.11:scalafmt  : 0.3.0  -> 0.3.1   -> 0.6.8  -> 1.5.1
      """.stripMargin.trim
    parseSingleUpdates(str.linesIterator.toList) shouldBe
      List(
        Update.Single(GroupId("ai.x"), "diff", "1.2.0", Nel.of("1.2.1"), Some("test")),
        Update
          .Single(
            GroupId("com.geirsson"),
            "scalafmt-cli_2.11",
            "0.3.0",
            Nel.of("0.3.1", "0.6.8", "1.5.1"),
            Some("scalafmt")
          ),
        Update.Single(GroupId("eu.timepit"), "refined", "0.7.0", Nel.of("0.9.3"))
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
        Update.Single(GroupId("com.github.pureconfig"), "pureconfig", "0.8.0", Nel.of("0.9.2")),
        Update.Single(GroupId("org.scala-lang"), "scala-library", "2.12.3", Nel.of("2.12.6")),
        Update.Single(GroupId("org.scalacheck"), "scalacheck", "1.13.5", Nel.of("1.14.0"))
      )
  }

  test("parseDependencies") {
    val lines =
      """|[info] core / libraryDependenciesAsJson
         |[info] 	[ { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.7" }, { "groupId": "com.github.pathikrit", "artifactId": "better-files", "artifactIdCross": "better-files_2.12", "version": "3.6.0" }, { "groupId": "org.typelevel", "artifactId": "cats-effect", "artifactIdCross": "cats-effect_2.12", "version": "1.0.0" } ]
         |sbt:project> libraryDependenciesAsJson
         |[info] [ { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.6" }, { "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "artifactIdCross": "sbt-travisci", "version": "1.1.3",  "sbtVersion": "1.0" }, { "groupId": "com.eed3si9n", "artifactId": "sbt-assembly", "artifactIdCross": "sbt-assembly", "version": "0.14.8", "sbtVersion": "1.0", "configurations": "foo" }, { "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "artifactIdCross": "sbt-scalafmt", "version": "1.6.0-RC4", "sbtVersion": "1.0" } ]
         |""".stripMargin.linesIterator.toList
    parseDependencies(lines) shouldBe List(
      Dependency(
        GroupId("org.scala-lang"),
        "scala-library",
        "scala-library",
        "2.12.7",
        None
      ),
      Dependency(
        GroupId("com.github.pathikrit"),
        "better-files",
        "better-files_2.12",
        "3.6.0",
        None
      ),
      Dependency(
        GroupId("org.typelevel"),
        "cats-effect",
        "cats-effect_2.12",
        "1.0.0",
        None
      ),
      Dependency(
        GroupId("org.scala-lang"),
        "scala-library",
        "scala-library",
        "2.12.6",
        None
      ),
      Dependency(
        GroupId("com.dwijnand"),
        "sbt-travisci",
        "sbt-travisci",
        "1.1.3",
        Some(SbtVersion("1.0"))
      ),
      Dependency(
        GroupId("com.eed3si9n"),
        "sbt-assembly",
        "sbt-assembly",
        "0.14.8",
        Some(SbtVersion("1.0")),
        Some("foo")
      ),
      Dependency(
        GroupId("com.geirsson"),
        "sbt-scalafmt",
        "sbt-scalafmt",
        "1.6.0-RC4",
        Some(SbtVersion("1.0"))
      )
    )
  }
}
