package org.scalasteward.core.edit

import munit.FunSuite

class UpdateHeuristicTest extends FunSuite {

  /*


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


  test("word from groupId") {
    val original = """val acolyteVersion = "1.0.49" """
    val expected = """val acolyteVersion = "1.0.51" """
    val update = ("org.eu.acolyte".g % "jdbc-driver".a % "1.0.49" %> "1.0.51").single
    assertEquals(update.replaceVersionIn(original), Some(expected) -> UpdateHeuristic.groupId.name)
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

  test("issue 960: unrelated ModuleID with same version number, 3") {
    val original = """ "org.webjars.npm" % "bootstrap" % "3.4.1", // scala-steward:off
                     | "org.webjars.npm" % "jquery" % "3.4.1",
                     |""".stripMargin
    val update = ("org.webjars.npm".g % "bootstrap".a % "3.4.1" %> "4.3.1").single
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


   */
}
