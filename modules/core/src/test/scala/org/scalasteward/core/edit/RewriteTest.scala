package org.scalasteward.core.edit

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{Repo, RepoData, Update}
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.scalafmtConfName
import org.scalasteward.core.util.Nel

class RewriteTest extends FunSuite {
  test("all on one line") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("build.sbt" -> """"be.doeraene" %% "scalajs-jquery"  % "0.9.3"""")
    val expected = Map("build.sbt" -> """"be.doeraene" %% "scalajs-jquery"  % "0.9.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("all upper case val") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("build.sbt" -> """val SCALAJSJQUERYVERSION = "0.9.3"""")
    val expected = Map("build.sbt" -> """val SCALAJSJQUERYVERSION = "0.9.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("val with backticks") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("build.sbt" -> """val `scalajs-jquery-version` = "0.9.3"""")
    val expected = Map("build.sbt" -> """val `scalajs-jquery-version` = "0.9.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("ignore hyphen in artifactId") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("Version.scala" -> """val scalajsJqueryVersion = "0.9.3"""")
    val expected = Map("Version.scala" -> """val scalajsJqueryVersion = "0.9.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("just artifactId without version") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("build.sbt" -> """val scalajsjquery = "0.9.3"""")
    val expected = Map("build.sbt" -> """val scalajsjquery = "0.9.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("version val with line break") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("Versions.scala" -> """val scalajsJqueryVersion =
                                             |  "0.9.3"""".stripMargin)
    val expected = Map("Versions.scala" -> """val scalajsJqueryVersion =
                                             |  "0.9.4"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/circe/circe-config/pull/40
  test("typesafe config update and sbt-site with the same version") {
    val update = ("com.typesafe".g % "config".a % "1.3.3" %> "1.3.4").single
    val original = Map(
      "build.sbt" -> """val config = "1.3.3"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    val expected = Map(
      "build.sbt" -> """val config = "1.3.4"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    runApplyUpdate(update, original, expected)
  }

  test("unrelated val with the same version") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("Versions.scala" -> """val scalajsJqueryVersion = "0.9.3"
                                             |val fooVersion = "0.9.3"""".stripMargin)
    val expected = Map("Versions.scala" -> """val scalajsJqueryVersion = "0.9.4"
                                             |val fooVersion = "0.9.3"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("group with repeated version") {
    val update =
      ("com.pepegar".g % Nel.of("hammock-core".a, "hammock-circe".a) % "0.8.1" %> "0.8.5").group
    val original = Map("build.sbt" -> """ "com.pepegar" %% "hammock-core"  % "0.8.1",
                                        | "com.pepegar" %% "hammock-circe" % "0.8.1"
                                        |""".stripMargin.trim)
    val expected = Map("build.sbt" -> """ "com.pepegar" %% "hammock-core"  % "0.8.5",
                                        | "com.pepegar" %% "hammock-circe" % "0.8.5"
                                        |""".stripMargin.trim)
    runApplyUpdate(update, original, expected)
  }

  test("sbt plugins") {
    val update = ("org.scala-js".g % "sbt-scalajs".a % "0.6.24" %> "0.6.25").single
    val original = Map(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    val expected = Map(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    runApplyUpdate(update, original, expected)
  }

  test("sbt plugins: missing version") {
    val update = ("org.scala-js".g % "sbt-scalajs".a % "0.6.24" %> "0.6.25").single
    val original = Map(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("sbt: build.properties") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.3.0-RC1" %> "1.3.0").single
    val original = Map("build.properties" -> """sbt.version=1.3.0-RC1""")
    val expected = Map("build.properties" -> """sbt.version=1.3.0""")
    runApplyUpdate(update, original, expected)
  }

  test("file restriction when sbt update") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.1.2" %> "1.2.8").single
    val original = Map(
      "build.properties" -> """sbt.version=1.1.2""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    val expected = Map(
      "build.properties" -> """sbt.version=1.2.8""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    runApplyUpdate(update, original, expected)
  }

  test("keyword with extra underscore") {
    val update =
      ("org.scala-js".g % Nel.of("sbt-scalajs".a, "scalajs-compiler".a) % "1.1.0" %> "1.1.1").group
    val original = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.0""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.0")"""
    )
    val expected = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.1""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.1")"""
    )
    runApplyUpdate(update, original, expected)
  }

  test("basic mill") {
    val update = ("com.lihaoyi".g % "requests".a % "0.7.0" %> "0.7.1").single
    val original = Map("build.sc" -> """ivy"com.lihaoyi::requests:0.7.0"""")
    val expected = Map("build.sc" -> """ivy"com.lihaoyi::requests:0.7.1"""")
    runApplyUpdate(update, original, expected)
  }

  test("mill with cross") {
    val update = ("com.goyeau".g % "mill-scalafix".a % "0.2.9" %> "0.2.10").single
    val original = Map("build.sc" -> """import $ivy.`com.goyeau::mill-scalafix::0.2.9`""")
    val expected = Map("build.sc" -> """import $ivy.`com.goyeau::mill-scalafix::0.2.10`""")
    runApplyUpdate(update, original, expected)
  }

  test("$ivy import") {
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val original = Map("build.sc" -> " import $ivy.`org.typelevel::cats-core:1.2.0` ")
    val expected = Map("build.sc" -> " import $ivy.`org.typelevel::cats-core:1.3.0` ")
    runApplyUpdate(update, original, expected)
  }

  test("$ivy import and sbt ModuleID") {
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val original = Map(
      "script.sc" -> """import $ivy.`org.typelevel::cats-core:1.2.0`, cats.implicits._""",
      "build.sbt" -> """"org.typelevel" %% "cats-core" % "1.2.0""""
    )
    val expected = Map(
      "script.sc" -> """import $ivy.`org.typelevel::cats-core:1.3.0`, cats.implicits._""",
      "build.sbt" -> """"org.typelevel" %% "cats-core" % "1.3.0""""
    )
    runApplyUpdate(update, original, expected)
  }

  test("mill with variable") {
    val update = ("com.lihaoyi".g % "requests".a % "0.7.0" %> "0.7.1").single
    val original = Map("build.sc" -> """val requests = "0.7.0"
                                       |ivy"com.lihaoyi::requests:$requests"
                                       |""".stripMargin)
    val expected = Map("build.sc" -> """val requests = "0.7.1"
                                       |ivy"com.lihaoyi::requests:$requests"
                                       |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/2664
  test("mill with generic variable") {
    val update = ("io.circe".g % Nel.of("circe-core".a) % "0.14.1" %> "0.14.2").group
    val original = Map("build.sc" -> """|val version = "0.14.1"
                                        |val core    = ivy"io.circe::circe-core:$version"
                                        |""".stripMargin)
    val expected = Map("build.sc" -> """|val version = "0.14.2"
                                        |val core    = ivy"io.circe::circe-core:$version"
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("mill with generic variable, 2") {
    val update = ("io.circe".g % Nel.of("circe-core".a) % "0.14.1" %> "0.14.2").group
    val original = Map("build.sc" -> """|val version = "0.14.1"
                                        |val core    = ivy"io.circe::circe-core:${version}"
                                        |""".stripMargin)
    val expected = Map("build.sc" -> """|val version = "0.14.2"
                                        |val core    = ivy"io.circe::circe-core:${version}"
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("mill version file update") {
    val update = ("com.lihaoyi".g % "mill-main".a % "0.9.5" %> "0.9.9").single
    val original = Map(
      ".mill-version" -> "0.9.5 \n ",
      ".travis.yml" -> """- TEST_MILL_VERSION=0.9.5"""
    )
    val expected = Map(
      ".mill-version" -> "0.9.9 \n ",
      ".travis.yml" -> """- TEST_MILL_VERSION=0.9.5"""
    )
    runApplyUpdate(update, original, expected)
  }

  test("disable updates on single lines with 'off' (no 'on')") {
    val update =
      ("com.typesafe.akka".g % Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    val original =
      Map("build.sbt" -> """ "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
                           | "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
                           | """.stripMargin)
    val expected =
      Map("build.sbt" -> """ "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
                           | "com.typesafe.akka" %% "akka-testkit" % "2.5.0",
                           | """.stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("disable updates on multiple lines after 'off' (no 'on')") {
    val update =
      ("com.typesafe.akka".g % Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    val original = Map("build.sbt" -> """ // scala-steward:off
                                        | "com.typesafe.akka" %% "akka-actor" % "2.4.0",
                                        | "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
                                        | """.stripMargin)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("hash before 'off'") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.2.8" %> "1.4.3").single
    val original = Map("build.properties" -> """# scala-steward:off
                                               |sbt.version=1.2.8
                                               |""".stripMargin)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("update multiple lines between 'on' and 'off'") {
    val update = ("com.typesafe.akka".g %
      Nel.of("akka-actor".a, "akka-testkit".a, "akka-slf4j".a) % "2.4.20" %> "2.5.0").group
    val original =
      Map("build.sbt" -> """  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
                           |  // scala-steward:on
                           |  "com.typesafe.akka" %% "akka-slf4j" % "2.4.20" % "test"
                           |  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
                           |  """.stripMargin.trim)
    val expected =
      Map("build.sbt" -> """  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
                           |  // scala-steward:on
                           |  "com.typesafe.akka" %% "akka-slf4j" % "2.5.0" % "test"
                           |  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
                           |  """.stripMargin.trim)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/960
  test("unrelated ModuleID with same version number, 3") {
    val update = ("org.webjars.npm".g % "bootstrap".a % "3.4.1" %> "4.3.1").single
    val original =
      Map("build.sbt" -> """ "org.webjars.npm" % "bootstrap" % "3.4.1", // scala-steward:off
                           | "org.webjars.npm" % "jquery" % "3.4.1",
                           |""".stripMargin)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("match artifactId cross name in Maven dependency") {
    val update =
      ("io.chrisdavenport".g % ("log4cats", "log4cats_2.13").a % "1.1.1" %> "1.2.0").single
    val original = Map("pom.xml" -> """<groupId>io.chrisdavenport</groupId>
                                      |<artifactId>log4cats_2.13</artifactId>
                                      |<version>1.1.1</version>""".stripMargin)
    val expected = Map("pom.xml" -> """<groupId>io.chrisdavenport</groupId>
                                      |<artifactId>log4cats_2.13</artifactId>
                                      |<version>1.2.0</version>""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("camel case artifactId") {
    val update = ("com.zaxxer".g % "HikariCP".a % "3.3.0" %> "3.4.0").single
    val original = Map("Versions.scala" -> """val hikariVersion = "3.3.0" """)
    val expected = Map("Versions.scala" -> """val hikariVersion = "3.4.0" """)
    runApplyUpdate(update, original, expected)
  }

  test("substring of artifactId prefix") {
    val update = ("org.mongodb".g %
      Nel.of("mongodb-driver".a, "mongodb-driver-async".a, "mongodb-driver-core".a) %
      "3.7.0" %> "3.7.1").group
    val original = Map("build.sbt" -> """val mongoVersion = "3.7.0" """)
    val expected = Map("build.sbt" -> """val mongoVersion = "3.7.1" """)
    runApplyUpdate(update, original, expected)
  }

  test("artifact change with sbt ModuleID") {
    val update = ("org.spire-math".g % "kind-projector".a % "0.9.0" %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val original = Map("build.sbt" -> """ "org.spire-math" %% "kind-projector" % "0.9.0" """)
    val expected = Map("build.sbt" -> """ "org.typelevel" %% "kind-projector" % "0.10.0" """)
    runApplyUpdate(update, original, expected)
  }

  test("artifact change with Mill dependency") {
    val update = ("org.spire-math".g % "kind-projector".a % "0.9.0" %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val original = Map("build.sc" -> """ "org.spire-math::kind-projector:0.9.0" """)
    val expected = Map("build.sc" -> """ "org.typelevel::kind-projector:0.10.0" """)
    runApplyUpdate(update, original, expected)
  }

  test("test updating group id and version") {
    val update = ("com.github.mpilquist".g % "simulacrum".a % "0.19.0" %> "1.0.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("simulacrum"))
    val original = Map("build.sbt" -> """val simulacrum = "0.19.0"
                                        |"com.github.mpilquist" %% "simulacrum" % simulacrum
                                        |"""".stripMargin)
    val expected = Map("build.sbt" -> """val simulacrum = "1.0.0"
                                        |"org.typelevel" %% "simulacrum" % simulacrum
                                        |"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("test updating artifact id and version") {
    val update = ("com.test".g % "artifact".a % "1.0.0" %> "2.0.0").single
      .copy(newerGroupId = Some("com.test".g), newerArtifactId = Some("newer-artifact"))
    val original =
      Map("Dependencies.scala" -> """val testVersion = "1.0.0"
                                    |val test = "com.test" %% "artifact" % testVersion
                                    |"""".stripMargin)
    val expected =
      Map("Dependencies.scala" -> """val testVersion = "2.0.0"
                                    |val test = "com.test" %% "newer-artifact" % testVersion
                                    |"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1977
  test("artifact change: version and groupId/artifactId in different files") {
    val update = ("io.chrisdavenport".g % "log4cats".a % "1.1.1" %> "1.2.0").single
      .copy(newerGroupId = Some("org.typelevel".g))
    val original = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.1.1" """,
      "build.sbt" -> """ "io.chrisdavenport" %% "log4cats" % log4catsVersion """
    )
    val expected = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.2.0" """,
      "build.sbt" -> """ "org.typelevel" %% "log4cats" % log4catsVersion """
    )
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1974
  test("artifact change: group update") {
    val update1 = ("io.chrisdavenport".g % "log4cats-core".a % "1.2.1" %> "1.3.0").single
      .copy(newerGroupId = Some("org.typelevel".g))
    val update2 = ("io.chrisdavenport".g % "log4cats-slf4j".a % "1.2.1" %> "1.3.0").single
      .copy(newerGroupId = Some("org.typelevel".g))
    val update = Update.groupByGroupId(List(update1, update2)).head
    val original = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.2.1" """,
      "build.sbt" -> """ "io.chrisdavenport" %% "log4cats-core" % log4catsVersion
                       | "io.chrisdavenport" %% "log4cats-slf4j" % log4catsVersion """.stripMargin
    )
    val expected = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.3.0" """,
      "build.sbt" -> """ "org.typelevel" %% "log4cats-core" % log4catsVersion
                       | "org.typelevel" %% "log4cats-slf4j" % log4catsVersion """.stripMargin
    )
    runApplyUpdate(update, original, expected)
  }

  test("groupId and version change of Maven dependency") {
    val update = ("io.chrisdavenport".g % "log4cats".a % "1.1.1" %> "1.2.0").single
      .copy(newerGroupId = Some("org.typelevel".g))
    val original = Map("pom.xml" -> """<groupId>io.chrisdavenport</groupId>
                                      |<artifactId>log4cats</artifactId>
                                      |<version>1.1.1</version>""".stripMargin)
    val expected = Map("pom.xml" -> """<groupId>org.typelevel</groupId>
                                      |<artifactId>log4cats</artifactId>
                                      |<version>1.2.0</version>""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("artifactId and version change of Maven dependency with binary suffix") {
    val update = ("org.foo".g % ("log4cats", "log4cats_2.13").a % "1.1.1" %> "1.2.0").single
      .copy(newerArtifactId = Some("log4dogs"))
    val original = Map("pom.xml" -> s"""<groupId>org.foo</groupId>
                                       |<artifactId>log4cats_$${scala.binary.version}</artifactId>
                                       |<version>1.1.1</version>""".stripMargin)
    val expected = Map("pom.xml" -> s"""<groupId>org.foo</groupId>
                                       |<artifactId>log4dogs_$${scala.binary.version}</artifactId>
                                       |<version>1.2.0</version>""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/pull/3016
  test("artifact change with multiple artifactId cross names") {
    val update = ("com.pauldijou".g % Nel.of(
      ("jwt-core", "jwt-core_2.12").a,
      ("jwt-core", "jwt-core_2.13").a
    ) % "5.0.0" %> "9.2.0").single.copy(newerGroupId = Some("com.github.jwt-scala".g))
    val original = Map("build.sbt" -> """ "com.pauldijou" %% "jwt-core" % "5.0.0" """)
    val expected = Map("build.sbt" -> """ "com.github.jwt-scala" %% "jwt-core" % "9.2.0" """)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/pull/566
  test("prevent exception: named capturing group is missing trailing '}'") {
    val update =
      ("org.nd4j".g % Nel.of("nd4j-api".a, "nd4j-native-platform".a) % "0.8.0" %> "0.9.1").group
    val original = Map(
      "build.sbt" -> (""""org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.8.0"""")
    )
    val expected = Map(
      "build.sbt" -> (""""org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.9.1"""")
    )
    runApplyUpdate(update, original, expected)
  }

  test("version range") {
    val update = ("org.specs2".g % "specs2-core".a % "3.+" %> "4.3.4").single
    val original = Map("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")""")
    val expected = Map("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")""")
    runApplyUpdate(update, original, expected)
  }

  test("commented ModuleIDs") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original =
      Map("build.sbt" -> """ "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                           | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                           |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                           |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                           |"""".stripMargin)
    val expected =
      Map("build.sbt" -> """ "be.doeraene" %% "scalajs-jquery"  % "0.9.4"
                           | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                           |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.4")
                           |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                           |"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("commented val") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("Versions.scala" -> """// val scalajsJqueryVersion = "0.9.3"
                                             |val scalajsJqueryVersion = "0.9.3" //bla
                                             |"""".stripMargin.trim)
    val expected = Map("Versions.scala" -> """// val scalajsJqueryVersion = "0.9.3"
                                             |val scalajsJqueryVersion = "0.9.4" //bla
                                             |"""".stripMargin.trim)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/pull/793
  test("similar artifactIds and same version") {
    val update =
      ("org.typelevel".g % Nel.of("cats-core".a, "cats-laws".a) % "2.0.0-M4" %> "2.0.0-RC1").group
    val original =
      Map("build.sbt" -> """ "org.typelevel" %%% "cats-core" % "2.0.0-M4",
                           | "org.typelevel" %%% "cats-laws" % "2.0.0-M4" % "test",
                           | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
                           | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
                           |""".stripMargin)
    val expected =
      Map("build.sbt" -> """ "org.typelevel" %%% "cats-core" % "2.0.0-RC1",
                           | "org.typelevel" %%% "cats-laws" % "2.0.0-RC1" % "test",
                           | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
                           | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
                           |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("unrelated ModuleID with same version number") {
    val update = ("org.scala-sbt".g %
      Nel.of("sbt-launch".a, "scripted-plugin".a, "scripted-sbt".a) % "1.2.1" %> "1.2.4").group
    val original = Map("plugins.sbt" -> """ "com.geirsson" % "sbt-ci-release" % "1.2.1" """)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1314
  test("unrelated ModuleID with same version number, 2") {
    val update = ("org.scalameta".g % "sbt-scalafmt".a % "2.0.1" %> "2.0.7").single
    val original = Map("plugins.sbt" -> """val scalafmt = "2.0.1"
                                          |addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
                                          |""".stripMargin)
    val expected = Map("plugins.sbt" -> """val scalafmt = "2.0.7"
                                          |addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
                                          |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/128
  test("ignore 'previous' prefix") {
    val update =
      ("io.circe".g % Nel.of("circe-jawn".a, "circe-testing".a) % "0.10.0" %> "0.10.1").group
    val original = Map("build.sbt" -> """val circeVersion = "0.10.0"
                                        |val previousCirceIterateeVersion = "0.10.0"
                                        |""".stripMargin)
    val expected = Map("build.sbt" -> """val circeVersion = "0.10.1"
                                        |val previousCirceIterateeVersion = "0.10.0"
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("ignore mimaPreviousArtifacts") {
    val update = ("io.dropwizard.metrics".g %
      Nel.of("metrics-core".a, "metrics-healthchecks".a) % "4.0.1" %> "4.0.3").group
    val original = Map(
      "build.sbt" -> """"io.dropwizard.metrics" % "metrics-core" % "4.0.1"
                       |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
                       |""".stripMargin
    )
    val expected = Map(
      "build.sbt" -> """"io.dropwizard.metrics" % "metrics-core" % "4.0.3"
                       |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
                       |""".stripMargin
    )
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/236
  test("same version, same artifact prefix, different groupId") {
    val update =
      ("org.typelevel".g % Nel.of("jawn-json4s".a, "jawn-play".a) % "0.14.0" %> "0.14.1").group
    val original =
      Map("build.sbt" -> """ "org.http4s" %% "jawn-fs2" % "0.14.0"
                           | "org.typelevel" %% "jawn-json4s"  % "0.14.0",
                           | "org.typelevel" %% "jawn-play" % "0.14.0" """.stripMargin.trim)
    val expected =
      Map("build.sbt" -> """ "org.http4s" %% "jawn-fs2" % "0.14.0"
                           | "org.typelevel" %% "jawn-json4s"  % "0.14.1",
                           | "org.typelevel" %% "jawn-play" % "0.14.1" """.stripMargin.trim)
    runApplyUpdate(update, original, expected)
  }

  test("val with similar name") {
    val update = ("org.typelevel".g % "cats-core".a % "2.0.0" %> "2.1.0").single
    val original = Map("Dependencies.scala" -> """val cats = "2.0.0"
                                                 |val catsEffect2 = "2.0.0"
                                                 |"org.typelevel" %% "cats-core" % cats
                                                 |"org.typelevel" %% "cats-effect" % catsEffect2
                                                 |""".stripMargin)
    val expected = Map("Dependencies.scala" -> """val cats = "2.1.0"
                                                 |val catsEffect2 = "2.0.0"
                                                 |"org.typelevel" %% "cats-core" % cats
                                                 |"org.typelevel" %% "cats-effect" % catsEffect2
                                                 |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1184
  test("qualified val with similar name") {
    val update = ("org.typelevel".g % "cats-core".a % "2.0.0" %> "2.1.0").single
    val original =
      Map("Dependencies.scala" -> """val cats = "2.0.0"
                                    |val catsEffect2 = "2.0.0"
                                    |"org.typelevel" %% "cats-core" % Version.cats
                                    |"org.typelevel" %% "cats-effect" % Version.catsEffect2
                                    |""".stripMargin)
    val expected =
      Map("Dependencies.scala" -> """val cats = "2.1.0"
                                    |val catsEffect2 = "2.0.0"
                                    |"org.typelevel" %% "cats-core" % Version.cats
                                    |"org.typelevel" %% "cats-effect" % Version.catsEffect2
                                    |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/2675
  test("qualified val with similar name, 2") {
    val update =
      ("org.http4s".g % Nel.of("http4s-circe".a, "http4s-dsl".a) % "0.23.11" %> "0.23.12").group
    val original =
      Map("build.sbt" -> """val http4s = "0.23.11"
                           |val http4sOkHttp = "0.23.11"
                           |"org.http4s" %% "http4s-dsl"           % Versions.http4s
                           |"org.http4s" %% "http4s-okhttp-client" % Versions.http4sOkHttp
                           |"org.http4s" %% "http4s-circe"         % Versions.http4s
                           |""".stripMargin)
    val expected =
      Map("build.sbt" -> """val http4s = "0.23.12"
                           |val http4sOkHttp = "0.23.11"
                           |"org.http4s" %% "http4s-dsl"           % Versions.http4s
                           |"org.http4s" %% "http4s-okhttp-client" % Versions.http4sOkHttp
                           |"org.http4s" %% "http4s-circe"         % Versions.http4s
                           |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("version val and dependency definition in different files") {
    val update = ("org.typelevel".g % "cats-core".a % "2.0.0" %> "2.1.0").single
    val original = Map(
      "Versions.scala" -> """val foo = "2.0.0"""",
      "Build.sbt" -> """"org.typelevel" %% "cats-core" % Version.foo"""
    )
    val expected = Map(
      "Versions.scala" -> """val foo = "2.1.0"""",
      "Build.sbt" -> """"org.typelevel" %% "cats-core" % Version.foo"""
    )
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/502
  test("version that contains the current version as proper substring") {
    val artifactIds = Nel.of("keywords-each".a, "keywords-using".a)
    val update = ("com.thoughtworks.dsl".g % artifactIds % "1.2.0" %> "1.3.0").group
    val original =
      Map("build.sbt" -> """"com.thoughtworks.dsl" %%% "keywords-using" % "1.2.0"
                           |"com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd"
                           |""".stripMargin)
    val expected =
      Map("build.sbt" -> """"com.thoughtworks.dsl" %%% "keywords-using" % "1.3.0"
                           |"com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd"
                           |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("ignore '-core' suffix") {
    val update = ("org.specs2".g % "specs2-core".a % "4.2.0" %> "4.3.4").single
    val original = Map("build.sbt" -> """val specs2Version = "4.2.0"""")
    val expected = Map("build.sbt" -> """val specs2Version = "4.3.4"""")
    runApplyUpdate(update, original, expected)
  }

  test("use groupId if artifactId is 'core'") {
    val update = ("com.softwaremill.sttp".g % "core".a % "1.3.2" %> "1.3.3").single
    val original = Map("build.sbt" -> """lazy val sttpVersion = "1.3.2"""")
    val expected = Map("build.sbt" -> """lazy val sttpVersion = "1.3.3"""")
    runApplyUpdate(update, original, expected)
  }

  test("artifactId with underscore") {
    val update =
      ("com.github.alexarchambault".g % "scalacheck-shapeless_1.13".a % "1.1.6" %> "1.1.8").single
    val original = Map("build.sbt" -> """val scShapelessV = "1.1.6"""")
    val expected = Map("build.sbt" -> """val scShapelessV = "1.1.8"""")
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1489
  test("ignore word: scala") {
    val update = ("com.github.plokhotnyuk.jsoniter-scala".g %
      Nel.of("jsoniter-scala-core".a, "jsoniter-scala-macros".a) % "2.4.0" %> "2.4.1").group
    val original = Map("build.sbt" -> """ val jsoniter = "2.4.0"
                                        | addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
                                        |""".stripMargin)
    val expected = Map("build.sbt" -> """ val jsoniter = "2.4.1"
                                        | addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("chars of search term contained in other term") {
    val update = ("org.typelevel".g % "cats-core".a % "2.4.1" %> "2.4.2").single
    val original = Map("build.sbt" -> """val cats = "2.4.1"
                                        |val scalaReactJsTestState = "2.4.1"
                                        |""".stripMargin)
    val expected = Map("build.sbt" -> """val cats = "2.4.2"
                                        |val scalaReactJsTestState = "2.4.1"
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("group with prefix val") {
    val update = ("io.circe".g %
      Nel.of("circe-generic".a, "circe-literal".a, "circe-parser".a, "circe-testing".a) %
      "0.10.0-M1" %> "0.10.0-M2").group
    val original = Map("build.sbt" -> """ val circe = "0.10.0-M1" """)
    val expected = Map("build.sbt" -> """ val circe = "0.10.0-M2" """)
    runApplyUpdate(update, original, expected)
  }

  test("short groupIds") {
    val update =
      ("com.sky".g % Nel.of("akka-streams".a, "akka-streams-kafka".a) % "1.2.0" %> "1.3.0").group
    val original = Map("build.sbt" -> """|private val mapCommonsDeps = Seq(
                                         |    "akka-streams",
                                         |    "akka-streams-kafka"
                                         |  ).map("com.sky" %% _ % "1.2.0")
                                         |""".stripMargin)
    val expected = Map("build.sbt" -> """|private val mapCommonsDeps = Seq(
                                         |    "akka-streams",
                                         |    "akka-streams-kafka"
                                         |  ).map("com.sky" %% _ % "1.3.0")
                                         |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("artifactId with dot") {
    val update = ("org.webjars.bower".g % "plotly.js".a % "1.41.3" %> "1.43.2").single
    val original = Map("build.sbt" -> """ def plotlyJs = "1.41.3" """)
    val expected = Map("build.sbt" -> """ def plotlyJs = "1.43.2" """)
    runApplyUpdate(update, original, expected)
  }

  test("val with backticks") {
    val update = ("org.webjars.bower".g % "plotly.js".a % "1.41.3" %> "1.43.2").single
    val original = Map("build.sbt" -> """ val `plotly.js` = "1.41.3" """)
    val expected = Map("build.sbt" -> """ val `plotly.js` = "1.43.2" """)
    runApplyUpdate(update, original, expected)
  }

  test("word from artifactId") {
    val update =
      ("io.circe".g % ("circe-generic", "circe-generic_2.12").a % "0.9.3" %> "0.11.1").single
    val original = Map("build.sbt" -> """lazy val circeVersion = "0.9.3"""")
    val expected = Map("build.sbt" -> """lazy val circeVersion = "0.11.1"""")
    runApplyUpdate(update, original, expected)
  }

  test("word from groupId") {
    val update = ("org.eu.acolyte".g % "jdbc-driver".a % "1.0.49" %> "1.0.51").single
    val original = Map("build.sbt" -> """val acolyteVersion = "1.0.49" """)
    val expected = Map("build.sbt" -> """val acolyteVersion = "1.0.51" """)
    runApplyUpdate(update, original, expected)
  }

  test("artifactIds are common suffixes") {
    val update = ("com.github.japgolly.scalajs-react".g %
      Nel.of("core".a, "extra".a) % "1.2.3" %> "1.3.1").group
    val original = Map("build.sbt" -> """lazy val scalajsReactVersion = "1.2.3"
                                        |lazy val logbackVersion = "1.2.3"
                                        |""".stripMargin)
    val expected = Map("build.sbt" -> """lazy val scalajsReactVersion = "1.3.1"
                                        |lazy val logbackVersion = "1.2.3"
                                        |""".stripMargin)
    runApplyUpdate(update, original, expected)
  }

  test("artifactId with common suffix") {
    val update = ("co.fs2".g % "fs2-core".a % "1.0.2" %> "1.0.4").single
    val original = Map("build.sbt" -> """case _ => "1.0.2" """)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("ignore TLD") {
    val update = ("com.slamdata".g % "fs2-gzip".a % "1.0.1" %> "1.1.1").single
    val original = Map("build.sbt" -> """ "com.propensive" %% "contextual" % "1.0.1" """)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("ignore 'scala' substring") {
    val update = ("org.scalactic".g % "scalactic".a % "3.0.7" %> "3.0.8").single
    val original = Map("build.sbt" -> """ val scalaTestVersion = "3.0.7" """)
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("ignore short words") {
    val update = ("org.scala-sbt".g % "scripted-plugin".a % "1.2.7" %> "1.2.8").single
    val original = Map(".travis.yml" -> "SBT_VERSION=1.2.7")
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1586
  test("tracing value for opentracing library") {
    val update = ("com.colisweb".g % Nel.of(
      "scala-opentracing-core".a,
      "scala-opentracing-context".a,
      "scala-opentracing-http4s-server-tapir".a
    ) % "2.4.1" %> "2.5.0").group
    val original = Map("build.sbt" -> """val tracing = "2.4.1" """)
    val expected = Map("build.sbt" -> """val tracing = "2.5.0" """)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1651
  test("don't update in comments") {
    val update = ("org.scalatest".g % "scalatest".a % "3.2.0" %> "3.3.0").single
    val original = Map(
      "build.sbt" -> """val scalaTest = "3.2.0"  // scalaTest 3.2.0 is causing a failure on scala 2.13..."""
    )
    val expected = Map(
      "build.sbt" -> """val scalaTest = "3.3.0"  // scalaTest 3.2.0 is causing a failure on scala 2.13..."""
    )
    runApplyUpdate(update, original, expected)
  }

  test("cognito value for aws-java-sdk-cognitoidp artifact") {
    val update = ("com.amazonaws".g % "aws-java-sdk-cognitoidp".a % "1.11.690" %> "1.11.700").single
    val original = Map("build.sbt" -> """val cognito       = "1.11.690" """)
    val expected = Map("build.sbt" -> """val cognito       = "1.11.700" """)
    runApplyUpdate(update, original, expected)
  }

  test("missing enclosing quote before") {
    val update = ("org.typelevel".g % "cats-effect".a % "2.2.0" %> "2.3.0").single
    val original = Map(
      "build.sbt" -> """.add("scalatestplus", version = "3.2.2.0", org = "org.scalatestplus", "scalacheck-1-14")"""
    )
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("missing enclosing quote after") {
    val update = ("org.typelevel".g % "cats-effect".a % "2.2.0" %> "2.3.0").single
    val original = Map(
      "build.sbt" -> """.add("scalatestplus", version = "2.2.0.3", org = "org.scalatestplus", "scalacheck-1-14")"""
    )
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("version val with if/else") {
    val update = ("com.lihaoyi".g % "mill-scalalib".a % "0.10.9" %> "0.10.10").single
    val original = Map(
      "Dependencies.scala" -> """val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.9")
                                |val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
                                |""".stripMargin
    )
    val expected = Map(
      "Dependencies.scala" -> """val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.10")
                                |val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
                                |""".stripMargin
    )
    runApplyUpdate(update, original, expected)
  }

  test("specific to scalafmt: should be Scala version agnostic") {
    val update =
      ("org.scalameta".g % ("scalafmt-core", "scalafmt-core_2.12").a % "2.0.0" %> "2.0.1").single
    val original = Map(scalafmtConfName -> """version = "2.0.0" """)
    val expected = Map(scalafmtConfName -> """version = "2.0.1" """)
    runApplyUpdate(update, original, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/2947
  test(".scalafmt.conf in a subdirectory") {
    val update =
      ("org.scalameta".g % ("scalafmt-core", "scalafmt-core_2.12").a % "2.0.0" %> "2.0.1").single
    val original = Map(s"foo/$scalafmtConfName" -> """version = "2.0.0" """)
    val expected = Map(s"foo/$scalafmtConfName" -> """version = "2.0.1" """)
    runApplyUpdate(update, original, expected)
  }

  test("scalafmt.conf and other scalameta update") {
    val update = ("org.scalameta".g % "other-artifact".a % "2.0.0" %> "2.0.1").single
    val original = Map(scalafmtConfName -> """version=2.0.0""")
    val expected = original
    runApplyUpdate(update, original, expected)
  }

  test("scala-cli using lib directive") {
    val update = ("org.scalameta".g % "munit".a % "0.7.29" %> "0.8.0").single
    val original = Map("Hello.scala" -> """//> using lib "org.scalameta::munit:0.7.29"""")
    val expected = Map("Hello.scala" -> """//> using lib "org.scalameta::munit:0.8.0"""")
    runApplyUpdate(update, original, expected)
  }

  test("issue-2877: sbt using same version in a val and a literal using a Seq addition") {
    val update = ("org.scalatest".g % Nel.of(
      "scalatest".a,
      "scalactic".a
    ) % "3.2.13" %> "3.2.14").group
    val original = Map(
      "build.sbt" ->
        """
          |val ScalaTestVersion = "3.2.13"
          |libraryDependencies ++= Seq(
          |  "org.scalatest" %% "scalatest" % ScalaTestVersion,
          |  "org.scalatest" %% "scalactic" % "3.2.13"
          |)
          |""".stripMargin
    )
    val expected = Map(
      "build.sbt" ->
        """
          |val ScalaTestVersion = "3.2.14"
          |libraryDependencies ++= Seq(
          |  "org.scalatest" %% "scalatest" % ScalaTestVersion,
          |  "org.scalatest" %% "scalactic" % "3.2.14"
          |)
          |""".stripMargin
    )
    runApplyUpdate(update, original, expected)
  }

  test("issue-2877: sbt using same version in a val and a literal using individual additions") {
    val update = ("org.scalatest".g % Nel.of(
      "scalatest".a,
      "scalactic".a
    ) % "3.2.13" %> "3.2.14").group
    val original = Map(
      "build.sbt" ->
        """
          |val ScalaTestVersion = "3.2.13"
          |libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion
          |libraryDependencies += "org.scalatest" %% "scalactic" % "3.2.13"
          |""".stripMargin
    )
    val expected = Map(
      "build.sbt" ->
        """
          |val ScalaTestVersion = "3.2.14"
          |libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion
          |libraryDependencies += "org.scalatest" %% "scalactic" % "3.2.14"
          |""".stripMargin
    )
    runApplyUpdate(update, original, expected)
  }

  private def runApplyUpdate(
      update: Update.Single,
      files: Map[String, String],
      expected: Map[String, String]
  ): Unit = {
    val repo = Repo("edit-alg", s"runApplyUpdate-${nextInt()}")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    val state = MockState.empty
      .addFiles(filesInRepoDir.toSeq: _*)
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()
    val obtained = state.files
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
    assertEquals(obtained, expected)
  }

  private var counter = 0

  private def nextInt(): Int = {
    counter = counter + 1
    counter
  }
}
