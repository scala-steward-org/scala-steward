package org.scalasteward.core.buildtool.sbt

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class sbtTest extends AnyFunSuite with Matchers {
  test("scalaStewardScalafixOptions") {
    scalaStewardScalafixOptions(List("-P:semanticdb:synthetics:on")).content shouldBe
      """ThisBuild / scalacOptions ++= List("-P:semanticdb:synthetics:on")"""
  }
}
