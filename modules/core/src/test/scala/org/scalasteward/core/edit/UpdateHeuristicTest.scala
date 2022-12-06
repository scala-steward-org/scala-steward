package org.scalasteward.core.edit

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.UpdateHeuristicTest.UpdateOps
import org.scalasteward.core.util.Nel

class UpdateHeuristicTest extends FunSuite {
  test("just artifactId without version") {
    val original = """val scalajsjquery = "0.9.3""""
    val expected = """val scalajsjquery = "0.9.4""""
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("ignore '-core' suffix") {
    val original = """val specs2Version = "4.2.0""""
    val expected = """val specs2Version = "4.3.4""""
    val update = ("org.specs2".g % "specs2-core".a % "4.2.0" %> "4.3.4").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("use groupId if artifactId is 'core'") {
    val original = """lazy val sttpVersion = "1.3.2""""
    val expected = """lazy val sttpVersion = "1.3.3""""
    val update = ("com.softwaremill.sttp".g % "core".a % "1.3.2" %> "1.3.3").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("short groupIds") {
    val original = """|private val mapCommonsDeps = Seq(
                      |    "akka-streams",
                      |    "akka-streams-kafka"
                      |  ).map("com.sky" %% _ % "1.2.0")
                      |""".stripMargin
    val expected = """|private val mapCommonsDeps = Seq(
                      |    "akka-streams",
                      |    "akka-streams-kafka"
                      |  ).map("com.sky" %% _ % "1.3.0")
                      |""".stripMargin
    val update =
      ("com.sky".g % Nel.of("akka-streams".a, "akka-streams-kafka".a) % "1.2.0" %> "1.3.0").group
    assertEquals(
      update.replaceVersionIn(original),
      Some(expected) -> UpdateHeuristic.completeGroupId.name
    )
  }

  test("group with prefix val") {
    val original = """ val circe = "0.10.0-M1" """
    val expected = """ val circe = "0.10.0-M2" """
    val update = ("io.circe".g %
      Nel.of("circe-generic".a, "circe-literal".a, "circe-parser".a, "circe-testing".a) %
      "0.10.0-M1" %> "0.10.0-M2").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("update under different group id") {
    val original = """ "org.spire-math" %% "kind-projector" % "0.9.0""""
    val expected = """ "org.typelevel" %% "kind-projector" % "0.10.0""""
    val update = ("org.spire-math".g % "kind-projector".a % "0.9.0" %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("group with repeated version") {
    val original =
      """ "com.pepegar" %% "hammock-core"  % "0.8.1",
        | "com.pepegar" %% "hammock-circe" % "0.8.1"
      """.stripMargin.trim
    val expected =
      """ "com.pepegar" %% "hammock-core"  % "0.8.5",
        | "com.pepegar" %% "hammock-circe" % "0.8.5"
      """.stripMargin.trim
    val update =
      ("com.pepegar".g % Nel.of("hammock-core".a, "hammock-circe".a) % "0.8.1" %> "0.8.5").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("artifactIds are common suffixes") {
    val original =
      """lazy val scalajsReactVersion = "1.2.3"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    val expected =
      """lazy val scalajsReactVersion = "1.3.1"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    val update = ("com.github.japgolly.scalajs-react".g %
      Nel.of("core".a, "extra".a) % "1.2.3" %> "1.3.1").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("ignore mimaPreviousArtifacts") {
    val original =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.1"
        |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
      """.stripMargin
    val expected =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.3"
        |mimaPreviousArtifacts := Set("io.dropwizard.metrics" %% "metrics-core" % "4.0.1")
      """.stripMargin
    val update = ("io.dropwizard.metrics".g %
      Nel.of("metrics-core".a, "metrics-healthchecks".a) % "4.0.1" %> "4.0.3").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("artifactId with dot") {
    val original = """ def plotlyJs = "1.41.3" """
    val expected = """ def plotlyJs = "1.43.2" """
    val update = ("org.webjars.bower".g % "plotly.js".a % "1.41.3" %> "1.43.2").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("val with backticks") {
    val original = """ val `plotly.js` = "1.41.3" """
    val expected = """ val `plotly.js` = "1.43.2" """
    val update = ("org.webjars.bower".g % "plotly.js".a % "1.41.3" %> "1.43.2").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("word from artifactId") {
    val original = """lazy val circeVersion = "0.9.3""""
    val expected = """lazy val circeVersion = "0.11.1""""
    val update =
      ("io.circe".g % ("circe-generic", "circe-generic_2.12").a % "0.9.3" %> "0.11.1").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("artifactId with underscore") {
    val original = """val scShapelessV = "1.1.6""""
    val expected = """val scShapelessV = "1.1.8""""
    val update =
      ("com.github.alexarchambault".g % "scalacheck-shapeless_1.13".a % "1.1.6" %> "1.1.8").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("camel case artifactId") {
    val original = """val hikariVersion = "3.3.0" """
    val expected = """val hikariVersion = "3.4.0" """
    val update = ("com.zaxxer".g % "HikariCP".a % "3.3.0" %> "3.4.0").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("mongo from mongodb") {
    val original = """val mongoVersion = "3.7.0" """
    val expected = """val mongoVersion = "3.7.1" """
    val update = ("org.mongodb".g %
      Nel.of("mongodb-driver".a, "mongodb-driver-async".a, "mongodb-driver-core".a) %
      "3.7.0" %> "3.7.1").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.sliding.name)
  }

  test("artifactId with common suffix") {
    val original = """case _ => "1.0.2" """
    val update = ("co.fs2".g % "fs2-core".a % "1.0.2" %> "1.0.4").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("word from groupId") {
    val original = """val acolyteVersion = "1.0.49" """
    val expected = """val acolyteVersion = "1.0.51" """
    val update = ("org.eu.acolyte".g % "jdbc-driver".a % "1.0.49" %> "1.0.51").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.groupId.name)
  }

  test("specific to scalafmt: should be Scala version agnostic") {
    Seq(
      ("""version = "2.0.0" """, """version = "2.0.1" """),
      ("""version="2.0.0"""", """version="2.0.1""""),
      ("""version = 2.0.0 """, """version = 2.0.1 """),
      ("""version=2.0.0 """, """version=2.0.1 """)
    ).foreach { case (original, expected) =>
      val update =
        ("org.scalameta".g % ("scalafmt-core", "scalafmt-core_2.12").a % "2.0.0" %> "2.0.1").single
      assertEquals(
        update.replaceVersionIn(original),
        Some(expected) -> UpdateHeuristic.specific.name
      )
    }

    val original = """version=2.0.0"""
    val update = ("org.scalameta".g % "other-artifact".a % "2.0.0" %> "2.0.1").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("ignore TLD") {
    val original = """ "com.propensive" %% "contextual" % "1.0.1" """
    val update = ("com.slamdata".g % "fs2-gzip".a % "1.0.1" %> "1.1.1").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("ignore short words") {
    val original = "SBT_VERSION=1.2.7"
    val update = ("org.scala-sbt".g % "scripted-plugin".a % "1.2.7" %> "1.2.8").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("ignore 'scala' substring") {
    val original = """ val scalaTestVersion = "3.0.7" """
    val update = ("org.scalactic".g % "scalactic".a % "3.0.7" %> "3.0.8").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("prevent exception: named capturing group is missing trailing '}'") {
    val original = """ "org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.8.0""""
    val expected = """ "org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.9.1""""
    val update =
      ("org.nd4j".g % Nel.of("nd4j-api".a, "nd4j-native-platform".a) % "0.8.0" %> "0.9.1").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.strict.name)
  }

  test("issue 960: unrelated ModuleID with same version number, 3") {
    val original = """ "org.webjars.npm" % "bootstrap" % "3.4.1", // scala-steward:off
                     | "org.webjars.npm" % "jquery" % "3.4.1",
                     |""".stripMargin
    val update = ("org.webjars.npm".g % "bootstrap".a % "3.4.1" %> "4.3.1").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("disable updates on single lines with `off` (no `on`)") {
    val original =
      """  "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
        |  """.stripMargin.trim
    val expected =
      """  "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.5.0",
        |  """.stripMargin.trim
    val update =
      ("com.typesafe.akka".g % Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("disable updates on multiple lines after `off` (no `on`)") {
    val original =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.0",
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
        |  """.stripMargin.trim
    val update = ("com.typesafe.akka".g %
      Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("update multiple lines between `on` and `off`") {
    val original =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
        |  // scala-steward:on
        |  "com.typesafe.akka" %% "akka-slf4j" % "2.4.20" % "test"
        |  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
        |  """.stripMargin.trim
    val expected =
      """  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
        |  // scala-steward:on
        |  "com.typesafe.akka" %% "akka-slf4j" % "2.5.0" % "test"
        |  // scala-steward:off
        |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
        |  """.stripMargin.trim
    val update = ("com.typesafe.akka".g %
      Nel.of("akka-actor".a, "akka-testkit".a, "akka-slf4j".a) % "2.4.20" %> "2.5.0").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.moduleId.name)
  }

  test("hash before `off`") {
    val original =
      """# scala-steward:off
        |sbt.version=1.2.8
        |""".stripMargin
    val update = ("org.scala-sbt".g % "sbt".a % "1.2.8" %> "1.4.3").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("cognito value for aws-java-sdk-cognitoidp artifact") {
    val original = """val cognito       = "1.11.690" """
    val expected = """val cognito       = "1.11.700" """
    val update = ("com.amazonaws".g % "aws-java-sdk-cognitoidp".a % "1.11.690" %> "1.11.700").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.sliding.name)
  }

  test("issue 1586: tracing value for opentracing library") {
    val original = """val tracing = "2.4.1" """
    val expected = """val tracing = "2.5.0" """
    val update = ("com.colisweb".g % Nel.of(
      "scala-opentracing-core".a,
      "scala-opentracing-context".a,
      "scala-opentracing-http4s-server-tapir".a
    ) % "2.4.1" %> "2.5.0").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.sliding.name)
  }

  test("issue 1489: ignore word: scala") {
    val original =
      """ val jsoniter = "2.4.0"
        | addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
        |""".stripMargin
    val expected =
      """ val jsoniter = "2.4.1"
        | addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
        |""".stripMargin
    val update = ("com.github.plokhotnyuk.jsoniter-scala".g %
      Nel.of("jsoniter-scala-core".a, "jsoniter-scala-macros".a) % "2.4.0" %> "2.4.1").group
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.relaxed.name)
  }

  test("missing enclosing quote before") {
    val original =
      """.add("scalatestplus", version = "3.2.2.0", org = "org.scalatestplus", "scalacheck-1-14")"""
    val update = ("org.typelevel".g % "cats-effect".a % "2.2.0" %> "2.3.0").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("missing enclosing quote after") {
    val original =
      """.add("scalatestplus", version = "2.2.0.3", org = "org.scalatestplus", "scalacheck-1-14")"""
    val update = ("org.typelevel".g % "cats-effect".a % "2.2.0" %> "2.3.0").single
    assertEquals(update.replaceVersionIn(original), None -> UpdateHeuristic.all.last.name)
  }

  test("issue 1651: don't update in comments") {
    val original =
      """val scalaTest = "3.2.0"  // scalaTest 3.2.0-M2 is causing a failure on scala 2.13..."""
    val expected =
      """val scalaTest = "3.2.2"  // scalaTest 3.2.0-M2 is causing a failure on scala 2.13..."""
    val update = ("org.scalatest".g % "scalatest".a % "3.2.0" %> "3.2.2").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("chars of search term contained in other term") {
    val original = """val cats = "2.4.1"
                     |val scalaReactJsTestState = "2.4.1"
                     |""".stripMargin
    val expected = """val cats = "2.4.2"
                     |val scalaReactJsTestState = "2.4.1"
                     |""".stripMargin
    val update = ("org.typelevel".g % "cats-core".a % "2.4.1" %> "2.4.2").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }

  test("mill with variable") {
    val original = """val requests = "0.7.0"
                     |ivy"com.lihaoyi::requests:$requests"
                     |""".stripMargin
    val expected = """val requests = "0.7.1"
                     |ivy"com.lihaoyi::requests:$requests"
                     |""".stripMargin
    val update = ("com.lihaoyi".g % "requests".a % "0.7.0" %> "0.7.1").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.original.name)
  }
}

object UpdateHeuristicTest {
  implicit class UpdateOps(update: Update.Single) {
    def replaceVersionIn(target: String): (Option[String], String) =
      UpdateHeuristic.all.foldLeft((Option.empty[String], "")) {
        case ((None, _), heuristic) => (heuristic.replaceVersion(update)(target), heuristic.name)
        case (result, _)            => result
      }
  }
}
