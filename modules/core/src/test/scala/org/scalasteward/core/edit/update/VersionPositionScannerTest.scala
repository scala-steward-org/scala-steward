package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.edit.update.data.Substring
import org.scalasteward.core.edit.update.data.VersionPosition._
import org.scalasteward.core.io.FileData

class VersionPositionScannerTest extends FunSuite {
  test("sbt module with newlines") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData(
      "build.sbt",
      s"""libraryDependencies += "${d.groupId}" %%
         |  "${d.artifactId.name}" %
         |  "${d.version}"""".stripMargin
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(
      SbtDependency(
        Substring.Position(fd.path, 61, d.version.value),
        "libraryDependencies += ",
        d.groupId.value,
        d.artifactId.name
      )
    )
    assertEquals(obtained, expected)
  }

  test("$ivy import") {
    val d = "org.typelevel".g % "cats-core".a % "1.2.0"
    val fd = FileData(
      "build.sc",
      s"""import $$ivy.`${d.groupId}::${d.artifactId.name}:${d.version}`, cats.implicits._"""
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(
      MillDependency(
        Substring.Position(fd.path, 38, d.version.value),
        "import $ivy.",
        d.groupId.value,
        d.artifactId.name
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt plugins 1") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val fd = FileData(
      "plugins.sbt",
      s"""addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")"""
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(
      SbtDependency(
        Substring.Position(fd.path, 47, d.version.value),
        "addSbtPlugin(",
        d.groupId.value,
        d.artifactId.name
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt plugins 2") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val fd = FileData(
      "plugins.sbt",
      s"""addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
         |addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")
         |addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.11")""".stripMargin
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(
      SbtDependency(
        Substring.Position(fd.path, 104, d.version.value),
        "addSbtPlugin(",
        d.groupId.value,
        d.artifactId.name
      )
    )
    assertEquals(obtained, expected)
  }

  test("simple val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData(
      "Versions.scala",
      s"""object Versions {
         |  val cats = "${d.version}"
         |}""".stripMargin
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(ScalaVal(Substring.Position(fd.path, 32, d.version.value), "  ", "cats"))
    assertEquals(obtained, expected)
  }

  test("commented val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData(
      "build.sbt",
      s"""object Versions {
         |  // val cats = "${d.version}"
         |}""".stripMargin
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(ScalaVal(Substring.Position(fd.path, 35, d.version.value), "  // ", "cats"))
    assertEquals(obtained, expected)
  }

  test("val with backticks") {
    val d = "org.webjars.bower".g % "plotly.js".a % "1.41.3"
    val fd = FileData("build.sbt", s""" val `plotly.js` = "${d.version}" """)
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected =
      List(ScalaVal(Substring.Position(fd.path, 20, d.version.value), " ", "`plotly.js`"))
    assertEquals(obtained, expected)
  }

  test("sbt version") {
    val d = "org.scala-sbt".g % "sbt".a % "1.2.8"
    val fd = FileData("build.properties", s"""sbt.version=${d.version}""")
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected =
      List(Unclassified(Substring.Position(fd.path, 12, d.version.value), "sbt.version="))
    assertEquals(obtained, expected)
  }

  test("unclassified 1") {
    val d = "org.scala-lang".g % "scala-compiler".a % "3.2.1-RC4"
    val fd = FileData(
      "",
      s"""scalaVersion := "${d.version}"
         |.target/scala-${d.version}/""".stripMargin
    )
    val obtained = VersionPositionScanner.findPositions(d.version, fd)
    val expected = List(
      Unclassified(Substring.Position(fd.path, 17, d.version.value), "scalaVersion := \""),
      Unclassified(Substring.Position(fd.path, 42, d.version.value), ".target/scala-")
    )
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1870
  test("unclassified with leading and trailing letters") {
    val version = "349".v
    val fd = FileData("", "6Eo349l6P")
    val obtained = VersionPositionScanner.findPositions(version, fd)
    val expected = List()
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/2412
  test("unclassified with trailing hyphen") {
    val version = "1.3.0".v
    val fd = FileData("test.yml", "1.3.0-1")
    val obtained = VersionPositionScanner.findPositions(version, fd)
    val expected = List()
    assertEquals(obtained, expected)
  }
}
