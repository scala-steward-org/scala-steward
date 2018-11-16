package eu.timepit.scalasteward.dependency
import eu.timepit.scalasteward.sbt.data.SbtVersion
import org.scalatest.{FunSuite, Matchers}

class parserTest extends FunSuite with Matchers {
  test("parseDependencies") {
    val input =
      """|[info] core / libraryDependenciesAsJson
         |[info] 	[ { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.7" }, { "groupId": "com.github.pathikrit", "artifactId": "better-files", "artifactIdCross": "better-files_2.12", "version": "3.6.0" }, { "groupId": "org.typelevel", "artifactId": "cats-effect", "artifactIdCross": "cats-effect_2.12", "version": "1.0.0" } ]
         |sbt:project> libraryDependenciesAsJson
         |[info] [ { "groupId": "org.scala-lang", "artifactId": "scala-library", "artifactIdCross": "scala-library", "version": "2.12.6" }, { "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "artifactIdCross": "sbt-travisci", "version": "1.1.3",  "sbtVersion": "1.0" }, { "groupId": "com.eed3si9n", "artifactId": "sbt-assembly", "artifactIdCross": "sbt-assembly", "version": "0.14.8", "sbtVersion": "1.0" }, { "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "artifactIdCross": "sbt-scalafmt", "version": "1.6.0-RC4", "sbtVersion": "1.0" } ]
         |""".stripMargin
    parser.parseDependencies(input) shouldBe List(
      Dependency(
        "org.scala-lang",
        "scala-library",
        "scala-library",
        "2.12.7",
        None
      ),
      Dependency(
        "com.github.pathikrit",
        "better-files",
        "better-files_2.12",
        "3.6.0",
        None
      ),
      Dependency(
        "org.typelevel",
        "cats-effect",
        "cats-effect_2.12",
        "1.0.0",
        None
      ),
      Dependency(
        "org.scala-lang",
        "scala-library",
        "scala-library",
        "2.12.6",
        None
      ),
      Dependency(
        "com.dwijnand",
        "sbt-travisci",
        "sbt-travisci",
        "1.1.3",
        Some(SbtVersion("1.0"))
      ),
      Dependency(
        "com.eed3si9n",
        "sbt-assembly",
        "sbt-assembly",
        "0.14.8",
        Some(SbtVersion("1.0"))
      ),
      Dependency(
        "com.geirsson",
        "sbt-scalafmt",
        "sbt-scalafmt",
        "1.6.0-RC4",
        Some(SbtVersion("1.0"))
      )
    )
  }
}
