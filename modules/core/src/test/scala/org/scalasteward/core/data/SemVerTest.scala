package org.scalasteward.core.data

import eu.timepit.refined.types.numeric.NonNegBigInt
import eu.timepit.refined.types.string.NonEmptyString
import munit.FunSuite
import org.scalasteward.core.data.SemVer.Change

class SemVerTest extends FunSuite {
  implicit val toNonNegBigInt: Int => NonNegBigInt =
    i => NonNegBigInt.unsafeFrom(BigInt(i))

  implicit val toNonEmptyString: String => NonEmptyString =
    NonEmptyString.unsafeFrom

  test("parse: simple examples") {
    assertEquals(SemVer.parse("1.2.3"), Some(SemVer(1, 2, 3, None, None)))
    assertEquals(SemVer.parse("0.0.0"), Some(SemVer(0, 0, 0, None, None)))
    assertEquals(SemVer.parse("123.456.789"), Some(SemVer(123, 456, 789, None, None)))
  }

  test("parse: with pre-release identifier") {
    assertEquals(SemVer.parse("1.0.0-SNAP5"), Some(SemVer(1, 0, 0, Some("SNAP5"), None)))
    assertEquals(SemVer.parse("9.10.100-0.3.7"), Some(SemVer(9, 10, 100, Some("0.3.7"), None)))
  }

  test("parse: empty pre-release identifier") {
    assertEquals(SemVer.parse("5.6.7-"), None)
  }

  test("parse: with build metadata") {
    assertEquals(
      SemVer.parse("1.0.0+20130313144700"),
      Some(SemVer(1, 0, 0, None, Some("20130313144700")))
    )
    assertEquals(
      SemVer.parse("1.0.0-beta+exp.sha.5114f85"),
      Some(SemVer(1, 0, 0, Some("beta"), Some("exp.sha.5114f85")))
    )
  }

  test("parse: empty build metadata") {
    assertEquals(SemVer.parse("6.7.8+"), None)
    assertEquals(SemVer.parse("9.10.11-M1+"), None)
  }

  test("parse: negative numbers") {
    assertEquals(SemVer.parse("-1.0.0"), None)
    assertEquals(SemVer.parse("0.-1.0"), None)
    assertEquals(SemVer.parse("0.0.-1"), None)
  }

  test("parse: leading zeros") {
    assertEquals(SemVer.parse("01.0.0"), None)
    assertEquals(SemVer.parse("0.01.0"), None)
    assertEquals(SemVer.parse("0.0.01"), None)
  }

  test("getChange") {
    assertEquals(
      SemVer.getChange(SemVer(1, 3, 4, None, None), SemVer(2, 1, 2, None, None)),
      Some(Change.Major)
    )
    assertEquals(
      SemVer.getChange(SemVer(2, 3, 4, None, None), SemVer(2, 5, 2, None, None)),
      Some(Change.Minor)
    )
    assertEquals(
      SemVer.getChange(
        SemVer(2, 3, 4, Some("SNAP1"), None),
        SemVer(2, 3, 4, Some("SNAP2"), None)
      ),
      Some(Change.PreRelease)
    )
    assertEquals(
      SemVer.getChange(
        SemVer(2, 3, 4, Some("M1"), Some("1")),
        SemVer(2, 3, 4, Some("M1"), Some("2"))
      ),
      Some(Change.BuildMetadata)
    )
    assertEquals(
      SemVer.getChange(
        SemVer(2, 3, 4, Some("M1"), None),
        SemVer(2, 3, 4, Some("M1"), None)
      ),
      None
    )
    assertEquals(
      SemVer.getChange(SemVer(0, 20, 0, Some("M4"), None), SemVer(0, 20, 3, None, None)),
      Some(Change.PreRelease)
    )
  }
}
