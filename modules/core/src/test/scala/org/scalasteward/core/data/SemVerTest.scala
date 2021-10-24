package org.scalasteward.core.data

import munit.FunSuite
import org.scalasteward.core.data.SemVer.Change

class SemVerTest extends FunSuite {
  test("parse: simple examples") {
    assertEquals(SemVer.parse("1.2.3"), Some(SemVer("1", "2", "3")))
    assertEquals(SemVer.parse("0.0.0"), Some(SemVer("0", "0", "0")))
    assertEquals(SemVer.parse("123.456.789"), Some(SemVer("123", "456", "789")))
  }

  test("parse: with pre-release identifier") {
    assertEquals(SemVer.parse("1.0.0-SNAP5"), Some(SemVer("1", "0", "0", Some("SNAP5"))))
    assertEquals(
      SemVer.parse("9.10.100-0.3.7"),
      Some(SemVer("9", "10", "100", Some("0.3.7")))
    )
  }

  test("parse: empty pre-release identifier") {
    assertEquals(SemVer.parse("5.6.7-"), None)
  }

  test("parse: with build metadata") {
    assertEquals(
      SemVer.parse("1.0.0+20130313144700"),
      Some(SemVer("1", "0", "0", None, Some("20130313144700")))
    )
    assertEquals(
      SemVer.parse("1.0.0-beta+exp.sha.5114f85"),
      Some(SemVer("1", "0", "0", Some("beta"), Some("exp.sha.5114f85")))
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

  test("getChangeSpec") {
    List(
      (SemVer("1", "3", "4"), SemVer("2", "1", "2"), Some(Change.Major)),
      (SemVer("2", "3", "4"), SemVer("2", "5", "2"), Some(Change.Minor)),
      (
        SemVer("2", "3", "4", Some("SNAP1")),
        SemVer("2", "3", "4", Some("SNAP2")),
        Some(Change.PreRelease)
      ),
      (
        SemVer("2", "3", "4", Some("M1"), Some("1")),
        SemVer("2", "3", "4", Some("M1"), Some("2")),
        Some(Change.BuildMetadata)
      ),
      (SemVer("2", "3", "4", Some("M1")), SemVer("2", "3", "4", Some("M1")), None),
      (SemVer("0", "20", "0", Some("M4")), SemVer("0", "20", "3"), Some(Change.PreRelease))
    ).foreach { case (from, to, result) =>
      assertEquals(SemVer.getChangeSpec(from, to), result)
    }
  }

  test("getChangeEarly") {
    List(
      (SemVer("1", "3", "4"), SemVer("2", "1", "2"), Some(Change.Major)),
      (SemVer("2", "3", "4"), SemVer("2", "5", "2"), Some(Change.Minor)),
      (
        SemVer("2", "3", "4", Some("SNAP1")),
        SemVer("2", "3", "4", Some("SNAP2")),
        Some(Change.PreRelease)
      ),
      (
        SemVer("2", "3", "4", Some("M1"), Some("1")),
        SemVer("2", "3", "4", Some("M1"), Some("2")),
        Some(Change.BuildMetadata)
      ),
      (SemVer("2", "3", "4", Some("M1")), SemVer("2", "3", "4", Some("M1")), None),
      (SemVer("0", "20", "0", Some("M4")), SemVer("0", "20", "3"), Some(Change.Minor)),
      (SemVer("0", "1", "0", None, None), SemVer("0", "2", "0", None, None), Some(Change.Major)),
      (SemVer("0", "0", "1", None, None), SemVer("0", "0", "2", None, None), Some(Change.Major)),
      (SemVer("0", "0", "0", None, None), SemVer("0", "0", "1", None, None), Some(Change.Major)),
      (SemVer("0", "0", "0", None, None), SemVer("0", "0", "0", None, None), None)
    ).foreach { case (from, to, result) =>
      assertEquals(SemVer.getChangeEarly(from, to), result)
    }
  }
}
