import sbt._

object Dependencies {
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.6.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val fs2Core = "co.fs2" %% "fs2-core" % "1.0.0-M5"
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Versions.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val refined = "eu.timepit" %% "refined" % "0.9.2"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
}
