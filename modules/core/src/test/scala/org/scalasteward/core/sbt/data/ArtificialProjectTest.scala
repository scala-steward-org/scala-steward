package org.scalasteward.core.sbt.data

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.sbt.command._
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite

class ArtificialProjectTest extends AnyFunSuite with Matchers {
  val catsEffect =
    Dependency("org.typelevel", "cats-effect", List("cats-effect_2.12"), "1.0.0")
  val scalaLibrary =
    Dependency("org.scala-lang", "scala-library", List("scala-library"), "2.12.6")
  val sbtTravisci =
    Dependency(
      "com.dwijnand",
      "sbt-travisci",
      List("sbt-travisci"),
      "1.1.3",
      sbtSeries = Some("1.0")
    )
  val sbtScalafmt =
    Dependency(
      "com.geirsson",
      "sbt-scalafmt",
      List("sbt-scalafmt"),
      "1.6.0-RC4",
      sbtSeries = Some("1.0")
    )
  val project = ArtificialProject(
    ScalaVersion("2.12.7"),
    SbtVersion("1.2.3"),
    List(catsEffect, scalaLibrary),
    List(sbtTravisci, sbtScalafmt)
  )

  test("dependencyUpdatesCmd") {
    project.dependencyUpdatesCmd shouldBe List(
      projectDependenciesWithUpdates,
      reloadPlugins,
      buildDependenciesWithUpdates
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
