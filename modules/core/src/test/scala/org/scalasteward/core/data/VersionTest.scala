package org.scalasteward.core.data

import cats.implicits._
import cats.kernel.Comparison.{EqualTo, GreaterThan, LessThan}
import cats.kernel.laws.discipline.OrderTests
import org.scalasteward.core.TestInstances._
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.typelevel.discipline.scalatest.Discipline

class VersionTest extends AnyFunSuite with Discipline with Matchers {
  checkAll("Order[Version]", OrderTests[Version].order)

  test("comparison") {
    val versions = Table(
      ("x", "y", "result"),
      ("1.0.0", "0.1", GreaterThan),
      ("1.8", "1.12", LessThan),
      ("1.2.3", "1.2.4", LessThan),
      ("1.2.3", "1.2.3", EqualTo),
      ("2.1", "2.1.3", LessThan),
      ("2.13.0-RC1", "2.13.0", LessThan),
      ("2.13.0-M2", "2.13.0", LessThan),
      ("2.13.0-M2", "2.13.0-RC1", LessThan),
      ("5.3.2.201906051522-r", "5.4.0.201906121030-r", LessThan),
      ("105", "104", GreaterThan),
      ("1.0.0+20130313", "1.0.0+20130320", LessThan),
      ("3.0.7-SNAP5", "3.0.7-RC1", LessThan)
    )

    forAll(versions) { (x, y, result) =>
      Version(x).comparison(Version(y)) shouldBe result
    }
  }

  test("selectNext, table 1") {
    val allVersions =
      List("1.0.0", "1.0.1", "1.0.2", "1.1.0", "1.2.0", "2.0.0", "3.0.0", "3.0.0.1", "3.1")
        .map(Version.apply)

    val nextVersions = Table(
      ("current", "result"),
      ("1.0.0", Some("1.0.2")),
      ("1.0.1", Some("1.0.2")),
      ("1.0.2", Some("1.2.0")),
      ("1.1.0", Some("1.2.0")),
      ("1.2.0", Some("3.1")),
      ("2.0.0", Some("3.1")),
      ("3.0.0", Some("3.0.0.1")),
      ("3.0.0.1", Some("3.1")),
      ("3.1", None),
      ("4", None)
    )

    forAll(nextVersions) { (current, result) =>
      Version(current).selectNext(allVersions) shouldBe result.map(Version.apply)
    }
  }

  test("selectNext, table 2") {
    val nextVersions = Table(
      ("current", "versions", "result"),
      ("1.3.0-RC3", List("1.3.0-RC4", "1.3.0-RC5"), Some("1.3.0-RC5")),
      ("1.3.0-RC3", List("1.3.0-RC4", "1.3.0-RC5", "1.3.0", "1.3.2"), Some("1.3.2")),
      ("3.0-RC3", List("3.0-RC4", "3.0-RC5", "3.0", "3.2"), Some("3.2")),
      ("1.3.0-RC5", List("1.3.0", "1.3.1", "1.3.2"), Some("1.3.2")),
      ("2.5", List("2.6", "3.6"), Some("2.6")),
      ("1.3.0-RC5", List("1.3.0", "1.4.0"), Some("1.3.0")),
      ("1.1.2-1", List("2.0.0", "2.0.1-M3"), Some("2.0.0")),
      ("0.19.0-RC1", List("0.20.0-RC1", "0.20.0"), Some("0.20.0")),
      ("0.19.0-RC1", List("0.20.0-RC1", "0.21.0-RC1"), None)
    )

    forAll(nextVersions) { (current, versions, result) =>
      Version(current).selectNext(versions.map(Version.apply)) shouldBe result.map(Version.apply)
    }
  }
}
