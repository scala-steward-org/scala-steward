package org.scalasteward.core.data

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import munit.DisciplineSuite
import org.scalacheck.Prop._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.Version.Component
import scala.util.Random

class VersionTest extends DisciplineSuite {
  checkAll("Order[Version]", OrderTests[Version].order)

  test("issue 1615: broken transitivity") {
    val res = OrderTests[Version].laws.transitivity(Version(""), Version("0"), Version("X"))
    assertEquals(res.lhs, res.rhs)
  }

  test("pairwise 1") {
    val versions = List(
      "0.1",
      "0-20170604",
      "1.0e",
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
      ("1.0e", "1.0.0-SNAP8"),
      ("1.0.1e", "1.0.1"),
      ("42.2.9.jre7", "42.2.9"),
      ("42.2.9.jre7", "42.2.9.jre8"),
      ("2.0.M6-SNAP23a", "2.0.M6-SNAP23"),
      ("2.13.0-M2", "2.13.0-RC1"),
      ("4.0RC1", "4.0.0"),
      ("1.7R5", "1.7"),
      ("1.7R5", "1.7.11"),
      ("14.0.2.1", "16-ea+2")
    ).foreach { case (s1, s2) =>
      val c1 = coursier.core.Version(s1)
      val c2 = coursier.core.Version(s2)
      assert(clue(c1) < clue(c2))
      assert(clue(c2) > clue(c1))

      val v1 = Version(s1)
      val v2 = Version(s2)
      assert(clue(v1) < clue(v2))
      assert(clue(v2) > clue(v1))
    }
  }

  test("equal") {
    assertEquals(Version("3.0").compare(Version("3.0.+")), 0)
    val empty: Component = Component.Empty
    assertEquals(empty.compare(empty), 0)
  }

  test("selectNext, table 1") {
    val allVersions =
      List("1.0.0", "1.0.1", "1.0.2", "1.1.0", "1.2.0", "2.0.0", "3.0.0", "3.0.0.1", "3.1")
        .map(Version.apply)

    val nextVersions = List(
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

    nextVersions.foreach { case (current, result) =>
      assertEquals(Version(current).selectNext(allVersions), result.map(Version.apply))
    }
  }

  test("selectNext, table 2") {
    val nextVersions = List(
      ("1.3.0-RC3", List("1.3.0-RC4", "1.3.0-RC5"), Some("1.3.0-RC5")),
      ("1.3.0-RC3", List("1.3.0-RC4", "1.3.0-RC5", "1.3.0", "1.3.2"), Some("1.3.2")),
      ("3.0-RC3", List("3.0-RC4", "3.0-RC5", "3.0", "3.2"), Some("3.2")),
      ("1.3.0-RC5", List("1.3.0", "1.3.1", "1.3.2"), Some("1.3.2")),
      ("2.5", List("2.6", "3.6"), Some("2.6")),
      ("3.8", List("3.9.4"), Some("3.9.4")),
      ("1.3.0-RC5", List("1.3.0", "1.4.0"), Some("1.3.0")),
      ("1.1.2-1", List("2.0.0", "2.0.1-M3"), Some("2.0.0")),
      ("0.19.0-RC1", List("0.20.0-RC1", "0.20.0"), Some("0.20.0")),
      ("0.19.0-RC1", List("0.20.0-RC1", "0.21.0-RC1"), None),
      ("1.14.0", List("1.14.1-RC1", "1.14.1", "1.14.2"), Some("1.14.2")),
      ("3.0.8-RC2", List("3.1.0-SNAP10"), None),
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
      ("9.4.29.v20200521", List("9.4.30.v20200611"), Some("9.4.30.v20200611")),
      ("2.0.0.AM4", List("2.0.0"), Some("2.0.0")),
      ("1.1.2-1", List("1.1.2"), None),
      ("1.1.2", List("1.1.2-1"), Some("1.1.2-1")),
      ("1.2.0+9-4a769501", List("1.2.0+17-7ef98061"), Some("1.2.0+17-7ef98061")),
      ("1.2.0+9-4a7695012", List("1.2.0+17-7ef9b061"), Some("1.2.0+17-7ef9b061")),
      ("1.2.0+9-4a76950123", List("1.2.0+17-7ef91060b"), Some("1.2.0+17-7ef91060b")),
      ("1.2.0+9-4a769501234", List("1.2.0+17-7bf9106e"), Some("1.2.0+17-7bf9106e")),
      ("0.5.1+29-8b2148d7", List("0.5.1+34-7033368d"), Some("0.5.1+34-7033368d")),
      (
        "0.21.6+43-2c1c1172-SNAPSHOT",
        List("0.21.6+75-6ad94f6f-SNAPSHOT"),
        Some("0.21.6+75-6ad94f6f-SNAPSHOT")
      ),
      ("1.2.0", List("1.2.0+17-7ef98061"), None),
      ("1.2.0+17-7ef98061", List("1.3.0"), Some("1.3.0")),
      ("2.4.4", List("3.0.0-preview"), None),
      ("2.3.2", List("2.3.3-b02"), None),
      ("2.3.2", List("2.3.3-b02-7ef9b061"), None),
      ("3.1.0", List("3.1.0-2156c0e"), None),
      ("3.1.0-2156c0e", List("3.2.0"), Some("3.2.0")),
      ("1.6.7", List("1.6.7-2-c28002d"), None),
      ("4.10.2", List("%5BWARNING%5D", ".5", ""), None),
      ("2.1.4-11-307f3d8", List("2.1.4-13-fb16e4e"), Some("2.1.4-13-fb16e4e")),
      ("2.1.4-13-fb16e4e", List("2.2.0", "2.2.0-0-fe5ed67"), Some("2.2.0")),
      ("2.2.0", List("2.2.0-0-fe5ed67", "2.2.0-4-4bd225e"), None),
      ("0.116.0-alpha", List("0.118.1-alpha"), None),
      ("0.8.0", List("0.8.0-1-d81662"), None),
      ("0.0.12", List("0.0.12-3-g699a2cf"), None),
      ("0.6.3", List("289f9e3aa3f5014a5c64319da8e6ab993947ade2-0-289f9e"), None),
      ("0.1-58d9629", List("0.8.0"), Some("0.8.0")),
      ("0.9-a3bf234", List("0.14-9419610"), Some("0.14-9419610")),
      ("0.0-ec56350", List("0.0-895b434"), None),
      ("2.0.0-M1", List("2.0-48e93de", "2.0.0-48e93de"), None),
      ("0.21.0-RC5", List("0.21.6", "0.21.6+43-2c1c1172-SNAPSHOT"), Some("0.21.6")),
      ("0.21.0-RC5", List("0.21.5", "0.21.6-RC1"), Some("0.21.6-RC1")),
      ("2.1.4.0-RC17", List("2.1.4.0-RC17+1-307f2f6c-SNAPSHOT"), None),
      ("v2-rev374-1.23.0", List("v2-rev20190917-1.30.3"), Some("v2-rev20190917-1.30.3")),
      ("1.0.0-20201119", List("1.0.0-20201208"), Some("1.0.0-20201208")),
      ("1.0.0-20201119-091040", List("1.0.0-20201208-143052"), Some("1.0.0-20201208-143052")),
      (
        "1.0.0-20201119-091040-d8b7496c",
        List("1.0.0-20201208-143052-2c1b1172"),
        Some("1.0.0-20201208-143052-2c1b1172")
      ),
      ("0.27.0-RC1", List("0.27.0-bin-20200826-2e58a66-NIGHTLY"), None),
      ("2.0.16-200-ge888c6dea", List("2.0.16-200-ge888c6dea-14-c067d59f0-SNAPSHOT"), None),
      ("17.0.0.1", List("18-ea+4"), None),
      ("", List("", ".", "1", "a"), Some("1")),
      ("1.4.12", List("1032048a", "1032048a4c2", "7d2bf0af+20171218-1522"), None),
      ("1.1", List("20000000"), None),
      ("10000000", List("20000000"), Some("20000000")),
      ("1032048a", List("2032048a4c2"), Some("2032048a4c2")),
      ("0.1.1-3dfde9d7", List("0.2.1-485fdf3b"), None),
      ("1.0.0+1319.ae77058", List("1.0.0+1320.38b57aa"), Some("1.0.0+1320.38b57aa")),
      ("0.1.1", List("0.2.1-485fdf3b"), None),
      ("0.1.1-ALPHA", List("0.2.1-485fdf3b"), None),
      ("0.1.1-ALPHA", List("0.2.1-BETA"), None),
      ("0.1.1", List("0.2.1-BETA"), None),
      ("0.1.1", List("0.2.0-FEAT"), None),
      ("0.1.1-FEAT", List("0.2.0"), Some("0.2.0")),
      ("0.1.1-FEAT+3dfde9d7", List("0.2.0"), Some("0.2.0"))
    )

    val rnd = new Random()
    nextVersions.foreach { case (current, versions, result) =>
      val obtained = Version(current).selectNext(rnd.shuffle(versions).map(Version.apply))
      assertEquals(obtained, result.map(Version.apply))
    }
  }

  test("selectNext, table PreReleases") {
    val nextVersions = List(
      ("0.1.1-3dfde9d7", List("0.2.1-485fdf3b"), Some("0.2.1-485fdf3b")),
      (
        "0.21.6+75-6ad94f6f-SNAPSHOT",
        List("0.21.6+99-a0087dd-SNAPSHOT"),
        Some("0.21.6+99-a0087dd-SNAPSHOT")
      ),
      (
        "0.21.6+75-6ad94f6f-SNAPSHOT",
        List("0.21.7+99-a0087dd-SNAPSHOT"),
        Some("0.21.7+99-a0087dd-SNAPSHOT")
      ),
      (
        "0.21.6+75-6ad94f6f-SNAPSHOT",
        List("0.22.0+99-a0087dd-SNAPSHOT"),
        Some("0.22.0+99-a0087dd-SNAPSHOT")
      ),
      (
        "0.21.6+75-6ad94f6f-SNAPSHOT",
        List("0.21.6+78-a0457da-SNAPSHOT", "0.21.7+81-a0ff7dd-SNAPSHOT"),
        Some("0.21.7+81-a0ff7dd-SNAPSHOT")
      ),
      (
        "0.21.6+75-6ad94f6f-SNAPSHOT",
        List("0.21.7+81-a0ff7dd-SNAPSHOT", "0.22.0+99-a0087dd-SNAPSHOT"),
        Some("0.22.0+99-a0087dd-SNAPSHOT")
      ),
      ("0.1.1", List("0.2.1-485fdf3b"), None),
      ("0.1.1-RC1", List("0.2.1-485fdf3b"), None),
      ("0.1.1-ALPHA", List("0.2.1-SNAPSHOT"), None),
      ("0.21.5", List("0.21.6+75-6ad94f6f-SNAPSHOT"), None),
      ("0.21.6", List("0.21.6+75-6ad94f6f-SNAPSHOT"), None),
      ("0.21.7", List("0.21.6+75-6ad94f6f-SNAPSHOT"), None),
      ("0.1.1-RC1", List("0.1.1-RC2"), Some("0.1.1-RC2")),
      ("0.1.1-RC1", List("0.1.2-RC1"), Some("0.1.2-RC1")),
      ("0.1.1", List("0.2.1-BETA"), Some("0.2.1-BETA")),
      ("0.1.1", List("0.2.0-M1"), Some("0.2.0-M1")),
      ("0.1.1-ALPHA", List("0.2.1-BETA"), Some("0.2.1-BETA")),
      ("0.1.1-3dfde9d7", List("0.1.2"), Some("0.1.2")),
      ("0.1.1", List("0.1.2"), Some("0.1.2")),
      ("0.1.1-RC1", List("0.1.2"), Some("0.1.2")),
      ("0.1.1-ALPHA", List("0.1.2"), Some("0.1.2")),
      ("0.1.1", List("0.1.2-FEAT"), Some("0.1.2-FEAT")),
      ("0.1.1", List("0.1.2-FEAT+3dfde9d7"), None)
    )

    val rnd = new Random()
    nextVersions.foreach { case (current, versions, result) =>
      val obtained = Version(current).selectNext(rnd.shuffle(versions).map(Version.apply), true)
      assertEquals(obtained, result.map(Version.apply))
    }
  }

  test("Component: round-trip") {
    forAll { str: String => assertEquals(Component.render(Component.parse(str)), str) }
  }

  test("Component: round-trip using Version") {
    forAll { v: Version => assertEquals(Component.render(Component.parse(v.value)), v.value) }
  }

  test("Component: round-trip example") {
    val original = "1.0.0-rc.1+build.1"
    assertEquals(Component.render(Component.Empty :: Component.parse(original)), original)
  }

  def checkPairwise(versions: List[String]): Unit = {
    val pairs = versions.tails.flatMap {
      case h :: t => t.map(v => (Version(h), Version(v)))
      case Nil    => Nil
    }
    pairs.foreach { case (v1, v2) =>
      assert(clue(v1) < clue(v2))
      assert(clue(v2) > clue(v1))
    }
  }
}
