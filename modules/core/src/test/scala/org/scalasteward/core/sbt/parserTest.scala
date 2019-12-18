package org.scalasteward.core.sbt

import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, RawUpdate}
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
         |{ "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "crossNames": [ ] }, "version": "2.12.7" }
         |{ "groupId": "com.github.pathikrit", "artifactId": { "name": "better-files", "crossNames": [ "better-files_2.12" ] }, "version": "3.6.0" }
         |{ "groupId": "org.typelevel", "artifactId": { "name": "cats-effect", "crossNames": [ "cats-effect_2.12" ] }, "version": "1.0.0" }
         |sbt:project> stewardDependencies
         |{ "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "crossNames": [ ] }, "version": "2.12.6" }
         |{ "groupId": "com.dwijnand", "artifactId": { "name": "sbt-travisci", "crossNames": [ ] }, "version": "1.1.3",  "sbtVersion": "1.0" }
         |{ "groupId": "com.eed3si9n", "artifactId": { "name": "sbt-assembly", "crossNames": [ ] }, "version": "0.14.8", "sbtVersion": "1.0", "configurations": "foo" }
         |{ "groupId": "com.geirsson", "artifactId": { "name": "sbt-scalafmt", "crossNames": [ ] }, "version": "1.6.0-RC4", "sbtVersion": "1.0" }
         |""".stripMargin.linesIterator.toList
    parseDependencies(lines) should contain theSameElementsAs List(
      Dependency(
        GroupId("org.scala-lang"),
        ArtifactId("scala-library"),
        "2.12.7",
        None
      ),
      Dependency(
        GroupId("com.github.pathikrit"),
        ArtifactId("better-files", "better-files_2.12"),
        "3.6.0",
        None
      ),
      Dependency(
        GroupId("org.typelevel"),
        ArtifactId("cats-effect", "cats-effect_2.12"),
        "1.0.0",
        None
      ),
      Dependency(
        GroupId("org.scala-lang"),
        ArtifactId("scala-library"),
        "2.12.6",
        None
      ),
      Dependency(
        GroupId("com.dwijnand"),
        ArtifactId("sbt-travisci"),
        "1.1.3",
        Some(SbtVersion("1.0"))
      ),
      Dependency(
        GroupId("com.eed3si9n"),
        ArtifactId("sbt-assembly"),
        "0.14.8",
        Some(SbtVersion("1.0")),
        None,
        Some("foo")
      ),
      Dependency(
        GroupId("com.geirsson"),
        ArtifactId("sbt-scalafmt"),
        "1.6.0-RC4",
        Some(SbtVersion("1.0"))
      )
    )
  }

  test("parseDependenciesAndUpdates") {
    val lines =
      """|[info] core / stewardDependencies
         |{ "groupId": "io.get-coursier", "artifactId": { "name": "coursier", "crossNames": [ "coursier_2.12" ] }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |{ "groupId": "io.get-coursier", "artifactId": { "name": "coursier-cats-interop", "crossNames": [ "coursier-cats-interop_2.12" ] }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |[info] core / stewardUpdates
         |{ "dependency": { "groupId": "io.get-coursier", "artifactId": { "name": "coursier", "crossNames": [ "coursier_2.12" ] }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |{ "dependency": { "groupId": "io.get-coursier", "artifactId": { "name": "coursier-cats-interop", "crossNames": [ "coursier-cats-interop_2.12" ] }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |""".stripMargin.linesIterator.toList

    val coursier =
      Dependency(GroupId("io.get-coursier"), ArtifactId("coursier", "coursier_2.12"), "2.0.0-RC5-2")
    val catsInterop =
      Dependency(
        GroupId("io.get-coursier"),
        ArtifactId("coursier-cats-interop", "coursier-cats-interop_2.12"),
        "2.0.0-RC5-2"
      )
    val (dependencies, updates) = parser.parseDependenciesAndUpdates(lines)
    dependencies should contain theSameElementsAs List(coursier, catsInterop)
    updates should contain theSameElementsAs List(
      RawUpdate(coursier, Nel.of("2.0.0-RC5-3")),
      RawUpdate(catsInterop, Nel.of("2.0.0-RC5-3"))
    )
  }
}
