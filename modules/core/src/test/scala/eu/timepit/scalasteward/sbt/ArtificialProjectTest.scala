package eu.timepit.scalasteward.sbt

import eu.timepit.scalasteward.dependency.Dependency
import org.scalatest.{FunSuite, Matchers}

class ArtificialProjectTest extends FunSuite with Matchers {
  val project = ArtificialProject(
    "2.12.7",
    "1.2.3",
    List(
      Dependency("org.typelevel", "cats-effect", "cats-effect_2.12", "1.0.0", "2.12.7", None),
      Dependency("org.scala-lang", "scala-library", "scala-library", "2.12.6", "2.12.6", None)
    ),
    List(
      Dependency("com.dwijnand", "sbt-travisci", "sbt-travisci", "1.1.3", "2.12", Some("1.0")),
      Dependency("com.geirsson", "sbt-scalafmt", "sbt-scalafmt", "1.6.0-RC4", "2.12", Some("1.0"))
    )
  )

  test("mkBuildSbt") {
    project.mkBuildSbt shouldBe
      """|scalaVersion := "2.12.7"
         |libraryDependencies += "org.typelevel" % "cats-effect_2.12" % "1.0.0"
         |libraryDependencies += "org.scala-lang" % "scala-library" % "2.12.6"
         |""".stripMargin.trim
  }

  test("mkPluginsSbt") {
    project.mkPluginsSbt shouldBe
      """|addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.1.3")
         |addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4")
         |""".stripMargin.trim
  }
}
