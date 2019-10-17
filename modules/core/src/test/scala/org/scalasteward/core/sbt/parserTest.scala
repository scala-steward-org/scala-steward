package org.scalasteward.core.sbt

import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.sbt.parser._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class parserTest extends AnyFunSuite with Matchers {
  test("parseBuildProperties: with whitespace") {
    parseBuildProperties("sbt.version = 1.2.8") shouldBe Some(SbtVersion("1.2.8"))
  }

  test("parseDependencies") {
    val lines =
      """|[info] core / stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": "scala-library", "crossArtifactIds": [ "scala-library" ], "version": "2.12.7" }
         |[info] { "groupId": "com.github.pathikrit", "artifactId": "better-files", "crossArtifactIds": [ "better-files_2.12" ], "version": "3.6.0" }
         |[info] { "groupId": "org.typelevel", "artifactId": "cats-effect", "crossArtifactIds": [ "cats-effect_2.12" ], "version": "1.0.0" }
         |sbt:project> stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": "scala-library", "crossArtifactIds": [ "scala-library" ], "version": "2.12.6" }
         |[info] { "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "crossArtifactIds": [ "sbt-travisci" ], "version": "1.1.3",  "sbtSeries": "1.0" }
         |[info] { "groupId": "com.eed3si9n", "artifactId": "sbt-assembly", "crossArtifactIds": [ "sbt-assembly" ], "version": "0.14.8", "sbtSeries": "1.0", "configurations": "foo" }
         |[info] { "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "crossArtifactIds": [ "sbt-scalafmt" ], "version": "1.6.0-RC4", "sbtSeries": "1.0" }
         |""".stripMargin.linesIterator.toList
    parseDependencies(lines).toSet shouldBe Set(
      Dependency(
        GroupId("org.scala-lang"),
        "scala-library",
        List("scala-library"),
        "2.12.7"
      ),
      Dependency(
        GroupId("com.github.pathikrit"),
        "better-files",
        List("better-files_2.12"),
        "3.6.0"
      ),
      Dependency(
        GroupId("org.typelevel"),
        "cats-effect",
        List("cats-effect_2.12"),
        "1.0.0"
      ),
      Dependency(
        GroupId("org.scala-lang"),
        "scala-library",
        List("scala-library"),
        "2.12.6"
      ),
      Dependency(
        GroupId("com.dwijnand"),
        "sbt-travisci",
        List("sbt-travisci"),
        "1.1.3",
        sbtVersion = Some(SbtVersion("1.0"))
      ),
      Dependency(
        GroupId("com.eed3si9n"),
        "sbt-assembly",
        List("sbt-assembly"),
        "0.14.8",
        configurations = Some("foo"),
        sbtVersion = Some(SbtVersion("1.0"))
      ),
      Dependency(
        GroupId("com.geirsson"),
        "sbt-scalafmt",
        List("sbt-scalafmt"),
        "1.6.0-RC4",
        sbtVersion = Some(SbtVersion("1.0"))
      )
    )
  }
}
