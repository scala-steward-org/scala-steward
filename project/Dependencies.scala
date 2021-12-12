import sbt._
import sbt.Keys._
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies {
  val bcprovJdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % "1.70"
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.9.1"
  val caseApp = "com.github.alexarchambault" %% "case-app" % "2.1.0-M10"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.3.0"
  val catsCore = "org.typelevel" %% "cats-core" % "2.7.0"
  val catsLaws = "org.typelevel" %% "cats-laws" % catsCore.revision
  val catsParse = "org.typelevel" %% "cats-parse" % "0.3.6"
  val circeConfig = "io.circe" %% "circe-config" % "0.8.0"
  val circeGeneric = "io.circe" %% "circe-generic" % "0.14.1"
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % "0.14.1"
  val circeLiteral = "io.circe" %% "circe-literal" % circeGeneric.revision
  val circeParser = "io.circe" %% "circe-parser" % circeGeneric.revision
  val circeRefined = "io.circe" %% "circe-refined" % circeGeneric.revision
  val commonsIo = "commons-io" % "commons-io" % "2.11.0"
  val coursierCore = "io.get-coursier" %% "coursier" % "2.0.16"
  val cron4sCore = "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.6.1"
  val disciplineMunit = "org.typelevel" %% "discipline-munit" % "1.0.9"
  val fs2Core = "co.fs2" %% "fs2-core" % "3.2.3"
  val fs2Io = "co.fs2" %% "fs2-io" % fs2Core.revision
  val http4sCore = "org.http4s" %% "http4s-core" % "1.0.0-M30"
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sCore.revision
  val http4sClient = "org.http4s" %% "http4s-client" % http4sCore.revision
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sCore.revision
  val http4sOkhttpClient = "org.http4s" %% "http4s-okhttp-client" % http4sCore.revision
  val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "2.1.1"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.7"
  val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % "0.11.2"
  val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % jjwtApi.revision
  val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % jjwtApi.revision
  val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.9.10")
  val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
  val monocleCore = "dev.optics" %% "monocle-core" % "3.1.0"
  val munit = "org.scalameta" %% "munit" % "0.7.29"
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-3" % "1.0.7"
  val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % munit.revision
  val refined = "eu.timepit" %% "refined" % "0.9.28"
  val refinedScalacheck = "eu.timepit" %% "refined-scalacheck" % refined.revision
  val scalacacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % "1.0.0-M5"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.15.4"
}
