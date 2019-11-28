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

  test("parseSingleUpdates: 3 updates") {
    val str =
      """[info] Found 3 dependency updates for datapackage
        |[info]  { "dependency": { "groupId": "ai.x", "artifactId": "diff", "artifactIdCross": "diff_2.11", "version": "1.2.0", "configurations": "test" }, "newerVersions": [ "1.2.1" ] }
        |[info]  { "dependency": { "groupId": "eu.timepit", "artifactId": "refined", "artifactIdCross": "refined_2.11", "version": "0.7.0" }, "newerVersions": [ "0.9.3" ] }
        |[info]  { "dependency": { "groupId": "com.geirsson", "artifactId": "scalafmt-cli_2.11", "artifactIdCross": "scalafmt-cli_2.11", "version": "0.3.0", "configurations": "scalafmt" }, "newerVersions": [ "0.3.1", "0.6.8", "1.5.1" ] }
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
    val lines =
      """|[info] Found 1 dependency update for refined",
         |[info]  { "dependency": { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.3" }, "newerVersions": [ "2.12.6" ] }
         |[info] Found 2 dependency updates for refined-scalacheck",
         |[info]  { "dependency": { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.3" }, "newerVersions": [ "2.12.6" ] }
         |[info]  { "dependency": { "groupId": "org.scalacheck", "artifactId": "scalacheck", "artifactIdCross": "scalacheck_2.12", "version": "1.13.5" }, "newerVersions": [ "1.14.0" ] }
         |[info] Found 2 dependency updates for refined-pureconfig",
         |[info]  { "dependency": { "groupId": "com.github.pureconfig", "artifactId": "pureconfig", "artifactIdCross": "pureconfig_2.12", "version": "0.8.0" }, "newerVersions": [ "0.9.2" ] }
         |""".stripMargin.linesIterator.toList
    parseSingleUpdates(lines) shouldBe
      List(
        Update.Single(GroupId("com.github.pureconfig"), "pureconfig", "0.8.0", Nel.of("0.9.2")),
        Update.Single(GroupId("org.scala-lang"), "scala-library", "2.12.3", Nel.of("2.12.6")),
        Update.Single(GroupId("org.scalacheck"), "scalacheck", "1.13.5", Nel.of("1.14.0"))
      )
  }

  test("parseDependencies") {
    val lines =
      """|[info] core / stewardDependencies
         |[info]  { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.7" }
         |[info]  { "groupId": "com.github.pathikrit", "artifactId": "better-files", "artifactIdCross": "better-files_2.12", "version": "3.6.0" }
         |[info]  { "groupId": "org.typelevel", "artifactId": "cats-effect", "artifactIdCross": "cats-effect_2.12", "version": "1.0.0" }
         |sbt:project> stewardDependencies
         |[info]  { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.6" }
         |[info]  { "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "artifactIdCross": "sbt-travisci", "version": "1.1.3",  "sbtVersion": "1.0" }
         |[info]  { "groupId": "com.eed3si9n", "artifactId": "sbt-assembly", "artifactIdCross": "sbt-assembly", "version": "0.14.8", "sbtVersion": "1.0", "configurations": "foo" }
         |[info]  { "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "artifactIdCross": "sbt-scalafmt", "version": "1.6.0-RC4", "sbtVersion": "1.0" }
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
        None,
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
