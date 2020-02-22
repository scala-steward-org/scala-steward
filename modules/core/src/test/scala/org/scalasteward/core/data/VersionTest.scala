package org.scalasteward.core.data

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.Version.Component
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import scala.util.Random

class VersionTest
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Matchers
    with ScalaCheckPropertyChecks {
  checkAll("Order[Version]", OrderTests[Version].order)

  test("pairwise 1") {
    val versions = List(
      "0.1",
      "0-20170604",
      "1.0.0-SNAP8",
      "1.0.0-M2",
      "1.0.0",
      "1.0.0+20130313",
      "1.0.0+20130320",
      "1.2",
      "1.2.3",
      "1.2.4",
      "1.2-20190102",
      "1.8",
      "1.12",
      "2.0.0-RC4",
      "2.0.0-RC4-1",
      "2.1",
      "2.1.3",
      "2.13.0-M2",
      "2.13.0-RC1",
      "2.13.0",
      "3.0.7-SNAP5",
      "3.0.7-RC1",
      "4.0RC1",
      "4.0",
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
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1+build.1",
      "1.0.0-rc.1",
      "1.0.0",
      "1.0.0+0.3.7",
      "1.0.0-20131213005945",
      "1.33.7+build",
      "1.33.7+build.2.b8f12d7",
      "1.33.7+build.11.e0f985a",
      "2.0.M5b",
      "2.0.M6-SNAP9",
      "2.0.M6-SNAP23a",
      "2.0.M6-SNAP23"
    )
    checkPairwise(versions)
  }

  test("pairwise 3") {
    // from https://semver.org/#spec-item-11
    val versions = List(
      "1.0.0-alpha.beta",
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-beta",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0"
    )
    checkPairwise(versions)
  }

  test("similar ordering as Coursier") {
    List(
      ("1.0.1e", "1.0.1"),
      ("42.2.9.jre7", "42.2.9"),
      ("42.2.9.jre7", "42.2.9.jre8"),
      ("2.0.M6-SNAP23a", "2.0.M6-SNAP23"),
      ("2.13.0-M2", "2.13.0-RC1"),
      ("4.0RC1", "4.0.0"),
      ("1.7R5", "1.7"),
      ("1.7R5", "1.7.11")
    ).foreach {
      case (s1, s2) =>
        val c1 = coursier.core.Version(s1)
        val c2 = coursier.core.Version(s2)
        c1 should be < c2
        c2 should be > c1

        val v1 = Version(s1)
        val v2 = Version(s2)
        v1 should be < v2
        v2 should be > v1
    }
  }

  test("equal") {
    Version("3.0").compare(Version("3.0.+")) shouldBe 0
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
      ("4.1.0.Beta7", List("5.0.0.Beta1"), None),
      ("8.0.192-R14", List("12.0.2-R18"), Some("12.0.2-R18")),
      ("8.0.192-RC14", List("8.0.192-R1"), Some("8.0.192-R1")),
      ("8.0.192-R14", List("12.0.2-RC18"), None),
      ("2.0.0-RC4", List("2.0.0-RC4-1"), Some("2.0.0-RC4-1")),
      ("2.0.0-RC4", List("2.1.0-RC4-1"), None),
      ("9.4.21.v20190926", List("10.0.0-alpha0"), None),
      ("2.0.0.AM4", List("2.0.0"), Some("2.0.0")),
      ("1.1.2-1", List("1.1.2"), None),
      ("1.2.0+9-4a769501", List("1.2.0+17-7ef98061"), Some("1.2.0+17-7ef98061")),
      ("1.2.0", List("1.2.0+17-7ef98061"), None),
      ("1.2.0+17-7ef98061", List("1.3.0"), Some("1.3.0")),
      ("2.4.4", List("3.0.0-preview"), None),
      ("2.3.2", List("2.3.3-b02"), None),
      ("3.1.0", List("3.1.0-2156c0e"), None),
      ("3.1.0-2156c0e", List("3.2.0"), Some("3.2.0")),
      ("1.6.7", List("1.6.7-2-c28002d"), None),
      ("4.10.2", List("%5BWARNING%5D"), None),
      ("v2-rev374-1.23.0", List("v2-rev20190917-1.30.3"), Some("v2-rev20190917-1.30.3"))
    )

    val rnd = new Random()
    forAll(nextVersions) { (current, versions, result) =>
      Version(current).selectNext(rnd.shuffle(versions).map(Version.apply)) shouldBe
        result.map(Version.apply)
    }
  }

  test("Component: round-trip") {
    forAll { str: String => Component.render(Component.parse(str)) shouldBe str }
  }

  test("Component: round-trip example") {
    val original = "1.0.0-rc.1+build.1"
    Component.render(Component.Empty :: Component.parse(original)) shouldBe original
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
