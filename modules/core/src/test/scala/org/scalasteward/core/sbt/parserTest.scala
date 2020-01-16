package org.scalasteward.core.sbt

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Resolver.{IvyRepository, MavenRepository}
import org.scalasteward.core.data.{ArtifactId, Scope, Update}
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
      """|[info] --- snip ---
         |[info] core / stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "maybeCrossName": null }, "version": "2.12.7" }
         |[info] { "groupId": "com.github.pathikrit", "artifactId": { "name": "better-files", "maybeCrossName": "better-files_2.12" }, "version": "3.6.0" }
         |[info] { "groupId": "org.typelevel", "artifactId": { "name": "cats-effect", "maybeCrossName": "cats-effect_2.12" }, "version": "1.0.0" }
         |[info] { "MavenRepository": { "name": "confluent-release", "location": "http://packages.confluent.io/maven/" } }
         |[info] { "MavenRepository": { "name": "bintray-ovotech-maven", "location": "https://dl.bintray.com/ovotech/maven/" } }
         |[info] --- snip ---
         |sbt:project> stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "maybeCrossName": null }, "version": "2.12.6" }
         |[info] { "groupId": "com.dwijnand", "artifactId": { "name": "sbt-travisci", "maybeCrossName": null }, "version": "1.1.3",  "sbtVersion": "1.0" }
         |[info] { "groupId": "com.eed3si9n", "artifactId": { "name": "sbt-assembly", "maybeCrossName": null }, "version": "0.14.8", "sbtVersion": "1.0", "configurations": "foo" }
         |[info] { "groupId": "com.geirsson", "artifactId": { "name": "sbt-scalafmt", "maybeCrossName": null }, "version": "1.6.0-RC4", "sbtVersion": "1.0" }
         |[info] { "IvyRepository" : { "name": "sbt-plugin-releases", "pattern": "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]" } }
         |[info] --- snip ---
         |""".stripMargin.linesIterator.toList
    val scopes = parseDependencies(lines)
    scopes shouldBe List(
      Scope(
        List(
          "org.scala-lang" % "scala-library" % "2.12.7",
          "com.github.pathikrit" % ArtifactId("better-files", "better-files_2.12") % "3.6.0",
          "org.typelevel" % ArtifactId("cats-effect", "cats-effect_2.12") % "1.0.0"
        ),
        List(
          MavenRepository("bintray-ovotech-maven", "https://dl.bintray.com/ovotech/maven/"),
          MavenRepository("confluent-release", "http://packages.confluent.io/maven/")
        )
      ),
      Scope(
        List(
          "org.scala-lang" % "scala-library" % "2.12.6",
          ("com.dwijnand" % "sbt-travisci" % "1.1.3").copy(sbtVersion = Some(SbtVersion("1.0"))),
          ("com.eed3si9n" % "sbt-assembly" % "0.14.8" % "foo")
            .copy(sbtVersion = Some(SbtVersion("1.0"))),
          ("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4").copy(sbtVersion = Some(SbtVersion("1.0")))
        ),
        List(
          IvyRepository(
            "sbt-plugin-releases",
            "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
          )
        )
      )
    )
  }

  test("parseDependenciesAndUpdates") {
    val lines =
      """|[info] core / stewardDependencies
         |[info] { "groupId": "io.get-coursier", "artifactId": { "name": "coursier", "maybeCrossName": "coursier_2.12" }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |[info] { "groupId": "io.get-coursier", "artifactId": { "name": "coursier-cats-interop", "maybeCrossName": "coursier-cats-interop_2.12" }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }
         |[info] core / stewardUpdates
         |[info] { "dependency": { "groupId": "io.get-coursier", "artifactId": { "name": "coursier", "maybeCrossName": "coursier_2.12" }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |[info] { "dependency": { "groupId": "io.get-coursier", "artifactId": { "name": "coursier-cats-interop", "maybeCrossName": "coursier-cats-interop_2.12" }, "version": "2.0.0-RC5-2", "configurations": null, "sbtVersion": null, "scalaVersion": null }, "newerVersions": [ "2.0.0-RC5-3" ] }
         |""".stripMargin.linesIterator.toList

    val coursier = "io.get-coursier" % ArtifactId("coursier", "coursier_2.12") % "2.0.0-RC5-2"
    val catsInterop = "io.get-coursier" %
      ArtifactId("coursier-cats-interop", "coursier-cats-interop_2.12") % "2.0.0-RC5-2"

    val (dependencies, updates) = parser.parseDependenciesAndUpdates(lines)
    dependencies should contain theSameElementsAs List(coursier, catsInterop)
    updates should contain theSameElementsAs List(
      Update.Single(coursier, Nel.of("2.0.0-RC5-3")),
      Update.Single(catsInterop, Nel.of("2.0.0-RC5-3"))
    )
  }
}
