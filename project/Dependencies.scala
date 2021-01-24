import sbt._
import sbt.Keys._
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies {
  val attoCore = "org.tpolecat" %% "atto-core" % "0.9.0"
  val bcprovJdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % "1.68"
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.9.1"
  val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"
  val caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.4"
  val catsEffect = "org.typelevel" %% "cats-effect" % "2.3.1"
  val catsCore = "org.typelevel" %% "cats-core" % "2.3.1"
  val catsLaws = "org.typelevel" %% "cats-laws" % catsCore.revision
  val circeConfig = "io.circe" %% "circe-config" % "0.8.0"
  val circeGeneric = "io.circe" %% "circe-generic" % "0.13.0"
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % "0.13.0"
  val circeLiteral = "io.circe" %% "circe-literal" % circeGeneric.revision
  val circeParser = "io.circe" %% "circe-parser" % circeGeneric.revision
  val circeRefined = "io.circe" %% "circe-refined" % circeGeneric.revision
  val commonsIo = "commons-io" % "commons-io" % "2.8.0"
  val coursierCore = "io.get-coursier" %% "coursier" % "2.0.9"
  val coursierCatsInterop = "io.get-coursier" %% "coursier-cats-interop" % coursierCore.revision
  val cron4sCore = "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.6.1"
  val disciplineMunit = "org.typelevel" %% "discipline-munit" % "1.0.4"
  val fs2Core = "co.fs2" %% "fs2-core" % "2.5.0"
  val fs2Io = "co.fs2" %% "fs2-io" % fs2Core.revision
  val http4sCore = "org.http4s" %% "http4s-core" % "0.21.16"
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sCore.revision
  val http4sClient = "org.http4s" %% "http4s-client" % http4sCore.revision
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sCore.revision
  val http4sOkhttpClient = "org.http4s" %% "http4s-okhttp-client" % http4sCore.revision
  val kindProjector = "org.typelevel" % "kind-projector" % "0.11.3"
  val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % "0.11.2"
  val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % jjwtApi.revision
  val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % jjwtApi.revision
  val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.9.4")
  val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
  val monocleCore = "com.github.julien-truffaut" %% "monocle-core" % "2.1.0"
  val munit = "org.scalameta" %% "munit" % "0.7.21"
  val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % munit.revision
  val refined = "eu.timepit" %% "refined" % "0.9.20"
  val refinedCats = "eu.timepit" %% "refined-cats" % refined.revision
  val refinedScalacheck = "eu.timepit" %% "refined-scalacheck" % refined.revision
  val scalacacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % "0.28.0"
  val scalacacheCatsEffect =
    "com.github.cb372" %% "scalacache-cats-effect" % scalacacheCaffeine.revision
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.15.2"
}
