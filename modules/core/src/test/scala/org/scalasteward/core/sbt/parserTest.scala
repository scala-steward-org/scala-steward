package org.scalasteward.core.sbt

import org.scalasteward.core.data.{Dependency, GroupId, RawUpdate}
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.sbt.parser._
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class parserTest extends AnyFunSuite with Matchers {
  test("parseBuildProperties: with whitespace") {
    parseBuildProperties("sbt.version = 1.2.8") shouldBe Some(SbtVersion("1.2.8"))
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

  test("parseDependenciesAndUpdates") {
    val lines =
      """|[info] core / stewardDependencies
         |[info] { "groupId": "io.get-coursier", "artifactId": "coursier", "artifactIdCross": "coursier_2.12", "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |[info] { "groupId": "io.get-coursier", "artifactId": "coursier-cats-interop", "artifactIdCross": "coursier-cats-interop_2.12", "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |[info] core / stewardUpdates
         |[info] { "dependency": { "groupId": "io.get-coursier", "artifactId": "coursier", "artifactIdCross": "coursier_2.12", "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |[info] { "dependency": { "groupId": "io.get-coursier", "artifactId": "coursier-cats-interop", "artifactIdCross": "coursier-cats-interop_2.12", "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |""".stripMargin.linesIterator.toList

    val coursier =
      Dependency(GroupId("io.get-coursier"), "coursier", "coursier_2.12", "2.0.0-RC5-2")
    val catsInterop =
      Dependency(
        GroupId("io.get-coursier"),
        "coursier-cats-interop",
        "coursier-cats-interop_2.12",
        "2.0.0-RC5-2"
      )
    val (dependencies, updates) = parser.parseDependenciesAndUpdates(lines)
    dependencies shouldBe List(coursier, catsInterop)
    updates shouldBe List(
      RawUpdate(coursier, Nel.of("2.0.0-RC5-3")),
      RawUpdate(catsInterop, Nel.of("2.0.0-RC5-3"))
    )
  }
}
