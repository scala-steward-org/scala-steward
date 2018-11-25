import sbt._

object Versions {
  val circe = "0.10.1"
  val http4s = "0.19.0"
}

object Dependencies {
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.6.0"
  val caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.0-M5"
  val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val circeRefined = "io.circe" %% "circe-refined" % Versions.circe
  val fs2Core = "co.fs2" %% "fs2-core" % "1.0.0"
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Versions.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val kindProjector = "org.spire-math" %% "kind-projector" % "0.9.9"
  val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % "0.2.0"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val refined = "eu.timepit" %% "refined" % "0.9.3"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
}
