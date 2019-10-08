package org.scalasteward.core.sbt.data

import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ArtificialProjectTest extends AnyFunSuite with Matchers {
  val catsEffect =
    Dependency(GroupId("org.typelevel"), "cats-effect", "cats-effect_2.12", "1.0.0", None)
  val scalaLibrary =
    Dependency(GroupId("org.scala-lang"), "scala-library", "scala-library", "2.12.6", None)
  val sbtTravisci =
    Dependency(
      GroupId("com.dwijnand"),
      "sbt-travisci",
      "sbt-travisci",
      "1.1.3",
      Some(SbtVersion("1.0"))
    )
  val sbtScalafmt =
    Dependency(
      GroupId("com.geirsson"),
      "sbt-scalafmt",
      "sbt-scalafmt",
      "1.6.0-RC4",
      Some(SbtVersion("1.0"))
    )
  val project = ArtificialProject(
    ScalaVersion("2.12.7"),
    SbtVersion("1.2.3"),
    List(catsEffect, scalaLibrary),
    List(sbtTravisci, sbtScalafmt)
  )

  test("dependencyUpdatesCmd") {
    project.dependencyUpdatesCmd shouldBe List(
      "dependencyUpdates",
      "reload plugins",
      "dependencyUpdates"
    )
  }

  test("mkBuildSbt") {
    project.mkBuildSbt.content shouldBe
      """|scalaVersion := "2.12.7"
         |libraryDependencies ++= Seq(
         |"org.typelevel" % "cats-effect_2.12" % "1.0.0",
         |"org.scala-lang" % "scala-library" % "2.12.6"
         |)
         |""".stripMargin.trim
  }

  test("mkBuildProperties") {
    project.mkBuildProperties.content shouldBe "sbt.version=1.2.3"
  }

  test("mkPluginsSbt") {
    project.mkPluginsSbt.content shouldBe
      """|addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.1.3")
         |addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4")
         |""".stripMargin.trim
  }

  test("halve: Some case") {
    project.halve shouldBe Some(
      (
        project.copy(libraries = List(catsEffect), plugins = List(sbtTravisci)),
        project.copy(libraries = List(scalaLibrary), plugins = List(sbtScalafmt))
      )
    )
  }

  test("halve: None case") {
    project.copy(libraries = List(catsEffect), plugins = List(sbtTravisci)).halve shouldBe None
  }
}
