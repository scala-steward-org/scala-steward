package org.scalasteward.core.scalafmt

import org.scalasteward.core.data.Version
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers}

class scalafmtTest extends FunSuite with Matchers {
  test("findNewerScalafmtVersion") {
    val versions = Table(
      ("in", "out"),
      (Version("2.0.0-RC8"), Some(latestScalafmtVersion)),
      (latestScalafmtVersion, None)
    )

    forAll(versions) { (curr: Version, maybeNewer: Option[Version]) =>
      findNewerScalafmtVersion(curr) shouldBe maybeNewer
    }
  }
}
