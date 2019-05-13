package org.scalasteward.core.model

import eu.timepit.refined.types.numeric.NonNegInt
import org.scalatest.{FunSuite, Matchers}

class SemVerTest extends FunSuite with Matchers {
  implicit val toNonNegInt: Int => NonNegInt = NonNegInt.unsafeFrom

  test("parse: simple examples") {
    SemVer.parse("1.2.3") shouldBe Some(SemVer(1, 2, 3, None, None))
    SemVer.parse("0.0.0") shouldBe Some(SemVer(0, 0, 0, None, None))
    SemVer.parse("123.456.789") shouldBe Some(SemVer(123, 456, 789, None, None))
  }

  test("parse: with pre-release identifier") {
    SemVer.parse("1.0.0-SNAP5") shouldBe Some(SemVer(1, 0, 0, Some("SNAP5"), None))
    SemVer.parse("9.10.100-0.3.7") shouldBe Some(SemVer(9, 10, 100, Some("0.3.7"), None))
  }

  test("parse: empty pre-release identifier") {
    SemVer.parse("5.6.7-") shouldBe None
  }

  test("parse: with build metadata") {
    SemVer.parse("1.0.0+20130313144700") shouldBe
      Some(SemVer(1, 0, 0, None, Some("20130313144700")))
    SemVer.parse("1.0.0-beta+exp.sha.5114f85") shouldBe
      Some(SemVer(1, 0, 0, Some("beta"), Some("exp.sha.5114f85")))
  }

  test("parse: empty build metadata") {
    SemVer.parse("6.7.8+") shouldBe None
    SemVer.parse("9.10.11-M1+") shouldBe None
  }

  test("parse: negative numbers") {
    SemVer.parse("-1.0.0") shouldBe None
    SemVer.parse("0.-1.0") shouldBe None
    SemVer.parse("0.0.-1") shouldBe None
  }

  test("parse: leading zeros") {
    SemVer.parse("01.0.0") shouldBe None
    SemVer.parse("0.01.0") shouldBe None
    SemVer.parse("0.0.01") shouldBe None
  }
}
