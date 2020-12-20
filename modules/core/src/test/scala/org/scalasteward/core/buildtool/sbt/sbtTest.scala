package org.scalasteward.core.buildtool.sbt

import munit.FunSuite

class sbtTest extends FunSuite {
  test("scalaStewardScalafixOptions") {
    assertEquals(
      scalaStewardScalafixOptions(List("-P:semanticdb:synthetics:on")).content,
      """ThisBuild / scalacOptions ++= List("-P:semanticdb:synthetics:on")"""
    )
  }
}
