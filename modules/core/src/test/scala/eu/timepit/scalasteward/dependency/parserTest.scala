package eu.timepit.scalasteward.dependency

import org.scalatest.{FunSuite, Matchers}

class parserTest extends FunSuite with Matchers {
  test("parseDependencies") {
    val input =
      """|[info] readme / libraryDependenciesAsJson
         |[info] 	[ { "groupId": "org.scala-lang", "artifactId": "scala-library", "version": "2.12.6", "scalaVersion": "2.12.6" } ,{ "groupId": "org.tpolecat", "artifactId": "tut-core", "version": "0.6.7", "scalaVersion": "2.12.6" } ,{ "groupId": "org.scoverage", "artifactId": "scalac-scoverage-runtime", "version": "1.3.1", "scalaVersion": "2.12.6" } ,{ "groupId": "org.scoverage", "artifactId": "scalac-scoverage-plugin", "version": "1.3.1", "scalaVersion": "2.12.6" } ]
         |sbt:project> libraryDependenciesAsJson
         |[info] [ { "groupId": "org.scala-lang", "artifactId": "scala-library", "version": "2.12.6", "scalaVersion": "2.12.6" } ,{ "groupId": "com.dwijnand", "artifactId": "sbt-travisci", "version": "1.1.3", "scalaVersion": "2.12", "sbtVersion": "1.0" } ,{ "groupId": "com.geirsson", "artifactId": "sbt-scalafmt", "version": "1.6.0-RC4", "scalaVersion": "2.12", "sbtVersion": "1.0" } ,{ "groupId": "org.scoverage", "artifactId": "sbt-scoverage", "version": "1.5.1", "scalaVersion": "2.12", "sbtVersion": "1.0" } ,{ "groupId": "org.tpolecat", "artifactId": "tut-plugin", "version": "0.6.7", "scalaVersion": "2.12", "sbtVersion": "1.0" } ]
         |""".stripMargin
    parser.parseDependencies(input) shouldBe List(
      Dependency("org.scala-lang", "scala-library", "2.12.6", "2.12.6"),
      Dependency("org.tpolecat", "tut-core", "0.6.7", "2.12.6"),
      Dependency("org.scoverage", "scalac-scoverage-runtime", "1.3.1", "2.12.6"),
      Dependency("org.scoverage", "scalac-scoverage-plugin", "1.3.1", "2.12.6"),
      Dependency("org.scala-lang", "scala-library", "2.12.6", "2.12.6"),
      Dependency("com.dwijnand", "sbt-travisci", "1.1.3", "2.12", Some("1.0")),
      Dependency("com.geirsson", "sbt-scalafmt", "1.6.0-RC4", "2.12", Some("1.0")),
      Dependency("org.scoverage", "sbt-scoverage", "1.5.1", "2.12", Some("1.0")),
      Dependency("org.tpolecat", "tut-plugin", "0.6.7", "2.12", Some("1.0"))
    )
  }
}
