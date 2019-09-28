package org.scalasteward.core.sbt

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.sbt.parser._
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite

class parserTest extends AnyFunSuite with Matchers {
  test("parseBuildProperties: with whitespace") {
    parseBuildProperties("sbt.version = 1.2.8") shouldBe Some(SbtVersion("1.2.8"))
  }

  test("parseDependencies") {
    val lines =
      """|[info] core / libraryDependenciesAsJson
         |[info] 	[ { "groupId": "org.scala-lang", "artifactId": "scala-library", "crossArtifactIds": [ "scala-library" ], "version": "2.12.7" }, { "groupId": "com.github.pathikrit", "artifactId": "better-files", "crossArtifactIds": [ "better-files_2.12" ], "version": "3.6.0" }, { "groupId": "org.typelevel", "artifactId": "cats-effect", "crossArtifactIds": [ "cats-effect_2.12" ], "version": "1.0.0" } ]
         |sbt:project> libraryDependenciesAsJson
         |[info] [ { "groupId": "org.scala-lang", "artifactId": "scala-library", "crossArtifactIds": [ "scala-library" ], "version": "2.12.6" }, { "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "crossArtifactIds": [ "sbt-travisci" ], "version": "1.1.3",  "sbtSeries": "1.0" }, { "groupId": "com.eed3si9n", "artifactId": "sbt-assembly", "crossArtifactIds": [ "sbt-assembly" ], "version": "0.14.8", "sbtSeries": "1.0", "configurations": "foo" }, { "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "crossArtifactIds": [ "sbt-scalafmt" ], "version": "1.6.0-RC4", "sbtSeries": "1.0" } ]
         |""".stripMargin.linesIterator.toList
    parseDependencies(lines).toSet shouldBe Set(
      Dependency(
        "org.scala-lang",
        "scala-library",
        List("scala-library"),
        "2.12.7"
      ),
      Dependency(
        "com.github.pathikrit",
        "better-files",
        List("better-files_2.12"),
        "3.6.0"
      ),
      Dependency(
        "org.typelevel",
        "cats-effect",
        List("cats-effect_2.12"),
        "1.0.0"
      ),
      Dependency(
        "org.scala-lang",
        "scala-library",
        List("scala-library"),
        "2.12.6"
      ),
      Dependency(
        "com.dwijnand",
        "sbt-travisci",
        List("sbt-travisci"),
        "1.1.3",
        sbtSeries = Some("1.0")
      ),
      Dependency(
        "com.eed3si9n",
        "sbt-assembly",
        List("sbt-assembly"),
        "0.14.8",
        configurations = Some("foo"),
        sbtSeries = Some("1.0")
      ),
      Dependency(
        "com.geirsson",
        "sbt-scalafmt",
        List("sbt-scalafmt"),
        "1.6.0-RC4",
        sbtSeries = Some("1.0")
      )
    )
  }
}
