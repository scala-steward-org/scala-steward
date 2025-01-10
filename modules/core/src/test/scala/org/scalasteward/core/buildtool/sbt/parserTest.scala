package org.scalasteward.core.buildtool.sbt

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.buildtool.sbt.parser.*
import org.scalasteward.core.data.Resolver.{Credentials, IvyRepository, MavenRepository}
import org.scalasteward.core.data.{Resolver, Scope, Version}

class parserTest extends FunSuite {
  test("parseBuildProperties: with whitespace") {
    assertEquals(parseBuildProperties("sbt.version = 1.2.8 "), Some(Version("1.2.8")))
  }

  test("parseDependencies") {
    val lines =
      """|[info] --- snip ---
         |[info] core / stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "maybeCrossName": null }, "version": "2.12.7" }
         |[info] { "groupId": "com.github.pathikrit", "artifactId": { "name": "better-files", "maybeCrossName": "better-files_2.12" }, "version": "3.6.0" }
         |[info] { "groupId": "org.typelevel", "artifactId": { "name": "cats-effect", "maybeCrossName": "cats-effect_2.12" }, "version": "1.0.0" }
         |[info] { "MavenRepository": { "name": "confluent-release", "location": "http://packages.confluent.io/maven/", "credentials": { "user": "donny", "pass": "brasc0" }, "headers": [] } }
         |[info] { "MavenRepository": { "name": "gitlab-internal", "location": "http://gitlab.example.com/maven/", "headers": [{ "key": "private-token", "value": "token123" }] } }
         |[info] { "MavenRepository": { "name": "bintray-ovotech-maven", "location": "https://dl.bintray.com/ovotech/maven/", "headers": [] } }
         |[info] --- snip ---
         |sbt:project> stewardDependencies
         |[info] { "groupId": "org.scala-lang", "artifactId": { "name": "scala-library", "maybeCrossName": null }, "version": "2.12.6" }
         |[info] { "groupId": "com.dwijnand", "artifactId": { "name": "sbt-travisci", "maybeCrossName": null }, "version": "1.1.3",  "sbtVersion": "1.0" }
         |[info] { "groupId": "com.eed3si9n", "artifactId": { "name": "sbt-assembly", "maybeCrossName": null }, "version": "0.14.8", "sbtVersion": "1.0", "configurations": "foo" }
         |{ "groupId": "org.scalameta", "artifactId": { "name": "sbt-scalafmt", "maybeCrossName": null }, "version": "2.4.6", "sbtVersion": "1.0", "scalaVersion": "2.12", "configurations": null }
         |[info] { "IvyRepository" : { "name": "sbt-plugin-releases", "pattern": "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]" } }
         |[info] { "IvyRepository" : { "name": "sbt-plugin-releases-with-creds", "pattern": "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]", "credentials": { "user": "tony", "pass": "m0ntana" }, "headers": [] } }
         |[info] --- snip ---
         |""".stripMargin.linesIterator.toList
    val scopes = parseDependencies(lines)
    val expected = List(
      Scope(
        List(
          "org.scala-lang".g % "scala-library".a % "2.12.7",
          "com.github.pathikrit".g % ("better-files", "better-files_2.12").a % "3.6.0",
          "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
        ),
        List(
          MavenRepository(
            "bintray-ovotech-maven",
            "https://dl.bintray.com/ovotech/maven/",
            None,
            Some(Nil)
          ),
          MavenRepository(
            "confluent-release",
            "http://packages.confluent.io/maven/",
            Some(Credentials("donny", "brasc0")),
            Some(Nil)
          ),
          MavenRepository(
            "gitlab-internal",
            "http://gitlab.example.com/maven/",
            None,
            Some(List(Resolver.Header("private-token", "token123")))
          )
        )
      ),
      Scope(
        List(
          "org.scala-lang".g % "scala-library".a % "2.12.6",
          ("com.dwijnand".g % "sbt-travisci".a % "1.1.3")
            .copy(sbtVersion = Some(SbtVersion("1.0"))),
          ("com.eed3si9n".g % "sbt-assembly".a % "0.14.8" % "foo")
            .copy(sbtVersion = Some(SbtVersion("1.0"))),
          ("org.scalameta".g % "sbt-scalafmt".a % "2.4.6")
            .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
        ),
        List(
          IvyRepository(
            "sbt-plugin-releases",
            "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]",
            None,
            None
          ),
          IvyRepository(
            "sbt-plugin-releases-with-creds",
            "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]",
            Some(Credentials("tony", "m0ntana")),
            Some(Nil)
          )
        )
      )
    )

    assertEquals(scopes, expected)
  }
}
