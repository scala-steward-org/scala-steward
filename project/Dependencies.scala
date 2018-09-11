import sbt._

object Dependencies {
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.6.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"
  val fs2Core = "co.fs2" %% "fs2-core" % "1.0.0-M5"
  val github4s = "com.47deg" %% "github4s" % Versions.github4s
  val github4sCatsEffect = "com.47deg" %% "github4s-cats-effect" % Versions.github4s
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
}
