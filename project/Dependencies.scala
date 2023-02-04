import sbt._

object Dependencies {
  val bcprovJdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % "1.72"
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.9.1"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.4.6"
  val catsCore = "org.typelevel" %% "cats-core" % "2.9.0"
  val catsLaws = "org.typelevel" %% "cats-laws" % catsCore.revision
  val catsParse = "org.typelevel" %% "cats-parse" % "0.3.9"
  val circeConfig = "io.circe" %% "circe-config" % "0.8.0"
  val circeGeneric = "io.circe" %% "circe-generic" % "0.14.3"
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % "0.14.3"
  val circeLiteral = "io.circe" %% "circe-literal" % circeGeneric.revision
  val circeParser = "io.circe" %% "circe-parser" % circeGeneric.revision
  val circeRefined = "io.circe" %% "circe-refined" % circeGeneric.revision
  val commonsIo = "commons-io" % "commons-io" % "2.11.0"
  val coursierCore = "io.get-coursier" %% "coursier" % "2.1.0-RC5"
  val cron4sCore = "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.6.1"
  val decline = "com.monovore" %% "decline" % "2.4.1"
  val disciplineMunit = "org.typelevel" %% "discipline-munit" % "1.0.9"
  val fs2Core = "co.fs2" %% "fs2-core" % "3.5.0"
  val fs2Io = "co.fs2" %% "fs2-io" % fs2Core.revision
  val http4sCore = "org.http4s" %% "http4s-core" % "1.0.0-M39"
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sCore.revision
  val http4sClient = "org.http4s" %% "http4s-client" % http4sCore.revision
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sCore.revision
  val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sCore.revision
  val http4sJdkhttpClient = "org.http4s" %% "http4s-jdk-http-client" % "1.0.0-M8"
  val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "2.5.0"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.4.5"
  val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % "0.11.5"
  val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % jjwtApi.revision
  val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % jjwtApi.revision
  val millVersion = "0.10.11"
  val millMain = "com.lihaoyi" % "mill-main_2.13" % millVersion
  val monocleCore = "dev.optics" %% "monocle-core" % "3.2.0"
  val munit = "org.scalameta" %% "munit" % "0.7.29"
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-3" % "1.0.7"
  val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % munit.revision
  val refined = "eu.timepit" %% "refined" % "0.10.1"
  val refinedScalacheck = "eu.timepit" %% "refined-scalacheck" % refined.revision
  val scalacacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % "1.0.0-M6"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.17.0"
  val scalaStewardMillPluginArtifactName = "scala-steward-mill-plugin"
  val scalaStewardMillPlugin =
    "org.scala-steward" % s"${scalaStewardMillPluginArtifactName}_mill0.10_2.13" % "0.17.1"
}
