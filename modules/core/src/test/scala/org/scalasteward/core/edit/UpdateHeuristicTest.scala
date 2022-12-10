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
   */
}
