package org.scalasteward.core.data

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.scalasteward.core.TestInstances._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.Discipline
import scala.util.Random

class VersionTest extends AnyFunSuite with Discipline with Matchers with ScalaCheckPropertyChecks {
  checkAll("Order[Version]", OrderTests[Version].order)

  test("pairwise 1") {
    val versions = List(
      "0-20170604",
      "0.1",
      "1.0.0",
      "1.0.0+20130313",
      "1.0.0+20130320",
      "1.2-20190102",
      "1.2",
      "1.2.3",
      "1.2.4",
      "1.8",
      "1.12",
      "2.1",
      "2.1.3",
      "2.13.0-M2",
      "2.13.0-RC1",
      "2.13.0",
      "3.0.7-SNAP5",
      "3.0.7-RC1",
      "5.3.2.201906051522-r",
      "5.4.0.201906121030-r",
      "104",
      "105"
    )
    checkPairwise(versions)
  }

  test("pairwise 2") {
    // from https://github.com/rtimush/sbt-updates/blob/44014898ad8548b08a9785bf78505945294c2ad6/src/test/scala/com/timushev/sbt/updates/versions/VersionSpec.scala#L35
    val versions = List(
      "1.0.0-20131213005945",
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0-rc.1+build.1",
      "1.0.0",
      "1.0.0+0.3.7",
      "1.33.7+build",
      "1.33.7+build.2.b8f12d7",
      "1.33.7+build.11.e0f985a",
      "2.0.M5b",
      "2.0.M6-SNAP9",
      "2.0.M6-SNAP23",
      "2.0.M6-SNAP23a"
    )
    checkPairwise(versions)
  }

  test("pairwise 3") {
    // from https://semver.org/#spec-item-11
    val versions = List(
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-alpha.beta",
      "1.0.0-beta",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0"
    )
    checkPairwise(versions)
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
      ("0.19.0-RC1", List("0.20.0-RC1", "0.21.0-RC1"), None),
      ("1.14.0", List("1.14.1-RC1", "1.14.1", "1.14.2"), Some("1.14.2")),
      ("3.1.0-SNAP13", List("3.2.0-M1"), None),
      ("3.1.0-SNAP13", List("3.2.0-M1", "3.2.0"), Some("3.2.0")),
      ("4.1.42.Final", List("5.0.0.Alpha2"), None),
      ("4.1.0.Beta7", List("4.1.0.Final"), Some("4.1.0.Final")),
      ("4.1.0.Beta7", List("5.0.0.Beta1"), None)
    )

    val rnd = new Random()
    forAll(nextVersions) { (current, versions, result) =>
      Version(current).selectNext(rnd.shuffle(versions).map(Version.apply)) shouldBe
        result.map(Version.apply)
    }
  }

  test("Component: round-trip") {
    forAll { str: String =>
      Version.Component.render(Version.Component.parse(str)) shouldBe str
    }
  }

  def checkPairwise(versions: List[String]): Unit = {
    val pairs = versions.tails.flatMap {
      case h :: t => t.map(v => (Version(h), Version(v)))
      case Nil    => Nil
    }
    pairs.foreach {
      case (v1, v2) =>
        v1 should be < v2
        v2 should be > v1
    }
  }
}
