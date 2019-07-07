package org.scalasteward.core.sbt

import org.scalasteward.core.sbt.data.SbtVersion
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

class sbtTest extends FunSuite with Matchers {
  test("findNewerSbtVersion") {
    val versions = Table(
      ("in", "out"),
      (SbtVersion("1.2.6"), Some(defaultSbtVersion)),
      (SbtVersion("1.1.0"), Some(defaultSbtVersion)),
      (SbtVersion("0.13.16"), Some(latestSbtVersion_0_13)),
      (defaultSbtVersion, None),
      (latestSbtVersion_0_13, None),
      (SbtVersion("1.3.0-RC1"), None)
    )

    forAll(versions) { (curr: SbtVersion, maybeNewer: Option[SbtVersion]) =>
      findNewerSbtVersion(curr) shouldBe maybeNewer
    }
  }
}
