package eu.timepit.scalasteward.sbt

import eu.timepit.scalasteward.dependency.Dependency
import org.scalatest.{FunSuite, Matchers}

class ArtificialProjectTest extends FunSuite with Matchers {
  val project = ArtificialProject(
    ScalaVersion("2.12.7"),
    SbtVersion("1.2.3"),
    List(
      Dependency(
        "org.typelevel",
        "cats-effect",
        "cats-effect_2.12",
        "1.0.0",
        ScalaVersion("2.12.7"),
        None
      ),
      Dependency(
        "org.scala-lang",
        "scala-library",
        "scala-library",
        "2.12.6",
        ScalaVersion("2.12.6"),
        None
      )
    ),
    List(
      Dependency(
        "com.dwijnand",
        "sbt-travisci",
        "sbt-travisci",
        "1.1.3",
        ScalaVersion("2.12"),
        Some(SbtVersion("1.0"))
      ),
      Dependency(
        "com.geirsson",
        "sbt-scalafmt",
        "sbt-scalafmt",
        "1.6.0-RC4",
        ScalaVersion("2.12"),
        Some(SbtVersion("1.0"))
      )
    )
  )

  test("dependencyUpdatesCmd") {
    project.dependencyUpdatesCmd shouldBe ";dependencyUpdates;reload plugins;dependencyUpdates"
  }

  test("mkBuildSbt") {
    project.mkBuildSbt.content shouldBe
      """|scalaVersion := "2.12.7"
         |libraryDependencies += "org.typelevel" % "cats-effect_2.12" % "1.0.0"
         |libraryDependencies += "org.scala-lang" % "scala-library" % "2.12.6"
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
}
