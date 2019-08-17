package org.scalasteward.core.scalafmt

import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.sbt.data.ScalaVersion
import org.scalatest.{FunSuite, Matchers}

class UtilTest extends FunSuite with Matchers {
  test("should not include minor version") {
    scalafmtDependency(ScalaVersion("2.12.9"))(Version("1.0")) shouldBe Some(
      Dependency("org.scalameta", "scalafmt-core", "scalafmt-core_2.12", "1.0")
    )
  }
}
