package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Version
import org.scalasteward.core.edit.update.data.SubstringPosition
import org.scalasteward.core.edit.update.data.VersionPosition._

class VersionScannerTest extends FunSuite {
  test("sbt module with newlines") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""libraryDependencies += "${d.groupId}" %%
                     |  "${d.artifactId.name}" %
                     |  "${d.version}"""".stripMargin
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(
      SbtDependency(
        SubstringPosition(61, d.version.value),
        "libraryDependencies += ",
        d.groupId.value,
        d.artifactId.name
      )
    )
    assertEquals(obtained, expected)
  }

  test("$ivy import") {
    val d = "org.typelevel".g % "cats-core".a % "1.2.0"
    val content =
      s"""import $$ivy.`${d.groupId}::${d.artifactId.name}:${d.version}`, cats.implicits._"""
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected =
      List(
        MillDependency(
          SubstringPosition(38, d.version.value),
          "import $ivy.",
          d.groupId.value,
          d.artifactId.name
        )
      )
    assertEquals(obtained, expected)
  }

  test("sbt plugins 1") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val content = s"""addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")"""
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected =
      List(
        SbtDependency(
          SubstringPosition(47, d.version.value),
          "addSbtPlugin(",
          d.groupId.value,
          d.artifactId.name
        )
      )
    assertEquals(obtained, expected)
  }

  test("sbt plugins 2") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val content = s"""addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
                     |addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")
                     |addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")""".stripMargin
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected =
      List(
        SbtDependency(
          SubstringPosition(104, d.version.value),
          "addSbtPlugin(",
          d.groupId.value,
          d.artifactId.name
        )
      )
    assertEquals(obtained, expected)
  }

  test("simple val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""object Versions {
                     |  val cats = "${d.version}"
                     |}""".stripMargin
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(ScalaVal(SubstringPosition(32, d.version.value), "  ", "cats"))
    assertEquals(obtained, expected)
  }

  test("commented val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""object Versions {
                     |  // val cats = "${d.version}"
                     |}""".stripMargin

    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(ScalaVal(SubstringPosition(35, d.version.value), "  // ", "cats"))
    assertEquals(obtained, expected)
  }

  test("val with backticks") {
    val d = "org.webjars.bower".g % "plotly.js".a % "1.41.3"
    val content = s""" val `plotly.js` = "${d.version}" """
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(ScalaVal(SubstringPosition(20, d.version.value), " ", "`plotly.js`"))
    assertEquals(obtained, expected)
  }

  test("sbt version") {
    val d = "org.scala-sbt".g % "sbt".a % "1.2.8"
    val content = s"""sbt.version=${d.version}"""
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(Unclassified(SubstringPosition(12, d.version.value), "sbt.version="))
    assertEquals(obtained, expected)
  }

  test("unclassified 1") {
    val d = "org.scala-lang".g % "scala-compiler".a % "3.2.1-RC4"
    val content = s"""scalaVersion := "${d.version}"
                     |.target/scala-${d.version}/""".stripMargin
    val obtained = VersionScanner.findPositions(d.version, content)
    val expected = List(
      Unclassified(SubstringPosition(17, d.version.value), "scalaVersion := \""),
      Unclassified(SubstringPosition(42, d.version.value), ".target/scala-")
    )
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1870
  test("unclassified with leading and trailing letters") {
    val version = Version("349")
    val content = "6Eo349l6P"
    val obtained = VersionScanner.findPositions(version, content)
    val expected = List()
    assertEquals(obtained, expected)
  }
}
