import sbt._

object Versions {
  val circe = "0.11.1"
  val http4s = "0.20.7"
  val refined = "0.9.9"
}

object Dependencies {
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.8.0"
  val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"
  val caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.0-M9"
  val catsEffect = "org.typelevel" %% "cats-effect" % "1.3.1"
  val catsKernelLaws = "org.typelevel" %% "cats-kernel-laws" % "1.6.1"
  val circeConfig = "io.circe" %% "circe-config" % "0.6.1"
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeLiteral = "io.circe" %% "circe-literal" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val circeRefined = "io.circe" %% "circe-refined" % Versions.circe
  val circeExtras = "io.circe" %% "circe-generic-extras" % Versions.circe
  val commonsIo = "commons-io" % "commons-io" % "2.6"
  val fs2Core = "co.fs2" %% "fs2-core" % "1.0.5"
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Versions.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % Versions.http4s
  val kindProjector = "org.typelevel" %% "kind-projector" % "0.10.3"
  val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % "0.3.0"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val monocleCore = "com.github.julien-truffaut" %% "monocle-core" % "1.5.1-cats"
  val refined = "eu.timepit" %% "refined" % Versions.refined
  val refinedCats = "eu.timepit" %% "refined-cats" % Versions.refined
  val refinedScalacheck = "eu.timepit" %% "refined-scalacheck" % Versions.refined
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"
}
