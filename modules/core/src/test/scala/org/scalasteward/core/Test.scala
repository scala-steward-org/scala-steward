package org.scalasteward.core

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.edit.VersionPosition.{SbtModuleId, ScalaVal, Unclassified}
import org.scalasteward.core.io.FilePosition
import org.scalasteward.core.scan.scanner

class Test extends FunSuite {
  test("sbt module with newlines") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""libraryDependencies += "${d.groupId}" %%
                     |  "${d.artifactId.name}" %
                     |  "${d.version}"""".stripMargin

    val obtained = scanner.findVersionPositions(d)(content)
    val expected = List(SbtModuleId(FilePosition(61, 3, 4)))
    assertEquals(obtained, expected)
  }

  test("sbt plugins 1") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val content = s"""addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")"""

    val obtained = scanner.findVersionPositions(d)(content)
    val expected = List(SbtModuleId(FilePosition(47, 1, 48)))
    assertEquals(obtained, expected)
  }

  test("sbt plugins 2") {
    val d = "org.scala-js".g % "sbt-scalajs".a % "0.6.23"
    val content = s"""addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
                     |addSbtPlugin("${d.groupId}" % "${d.artifactId.name}" % "${d.version}")
                     |addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")""".stripMargin

    val obtained = scanner.findVersionPositions(d)(content)
    val expected = List(SbtModuleId(FilePosition(104, 2, 48)))
    assertEquals(obtained, expected)
  }

  test("simple val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""object Versions {
                     |  val cats = "${d.version}"
                     |}""".stripMargin

    val obtained = scanner.findVersionPositions(d)(content)
    val expected = List(ScalaVal(FilePosition(32, 2, 15), "cats", "  "))
    assertEquals(obtained, expected)
  }

  test("commented val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s"""object Versions {
                     |  // val cats = "${d.version}"
                     |}""".stripMargin

    val obtained = scanner.findVersionPositions(d)(content)
    val expected = List(ScalaVal(FilePosition(35, 2, 18), "cats", "  // "))
    assertEquals(obtained, expected)
  }

  test("unclassified 1") {
    val d = "org.scala-lang".g % "scala-compiler".a % "3.2.1-RC4"
    val content = s"""scalaVersion := "${d.version.value}"
                     |.target/scala-${d.version.value}/""".stripMargin

    val obtained = scanner.findVersionPositions(d)(content)
    val expected =
      List(Unclassified(FilePosition(17, 1, 18)), Unclassified(FilePosition(42, 2, 15)))
    assertEquals(obtained, expected)
  }
}
