package org.scalasteward.core.edit

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{RepoData, Update}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.editAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

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

  /* TODO
  test("unrelated val with the same version") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = Map("Versions.scala" -> """val scalajsJqueryVersion = "0.9.3"
                                              |val fooVersion = "0.9.3"""".stripMargin)
    val expected = Map("Versions.scala" -> """val scalajsJqueryVersion = "0.9.4"
                                              |val fooVersion = "0.9.3"""".stripMargin)
    runApplyUpdate(update, original, expected)
  }
   */

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

  /*
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
   */

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

  private def runApplyUpdate(
      update: Update.Single,
      files: Map[String, String],
      expected: Map[String, String]
  ): Unit = {
    val repo = Repo("edit-alg", s"runApplyUpdate-${nextInt()}")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.toPath
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    val obtained = MockState.empty
      .addFiles(filesInRepoDir.toSeq: _*)
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
    assertEquals(obtained, expected)
  }

  private var counter = 0

  private def nextInt(): Int = {
    counter = counter + 1
    counter
  }
}

// com.lihaoyi:mill-scalalib from 0.10.9 to 0.10.10
// val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.10")
// val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.9")
// val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
