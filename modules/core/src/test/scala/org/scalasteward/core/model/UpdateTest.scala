package org.scalasteward.core.model

import org.scalasteward.core.model.Update.{Group, Single}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class UpdateTest extends FunSuite with Matchers {

  test("replaceAllIn: updated") {
    val original =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    val expected =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: not updated") {
    val orig =
      """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
        |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
        |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
      """.stripMargin.trim
    Single("org.scala-js", "sbt-scalajs", "0.6.24", Nel.of("0.6.25"))
      .replaceAllIn(orig) shouldBe None
  }

  test("replaceAllIn: ignore hyphen") {
    val original = """val scalajsJqueryVersion = "0.9.3""""
    val expected = """val scalajsJqueryVersion = "0.9.4""""
    Single("be.doeraene", "scalajs-jquery", "0.9.3", Nel.of("0.9.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: do not update comments") {
    val original =
      """// val scalajsJqueryVersion = "0.9.3
        |val scalajsJqueryVersion = "0.9.3 //bla
        | "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |"""".stripMargin.trim
    val expected =
      """// val scalajsJqueryVersion = "0.9.3
        |val scalajsJqueryVersion = "0.9.4 //bla
        | "be.doeraene" %% "scalajs-jquery"  % "0.9.4"
        | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
        |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.4")
        |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
        |"""".stripMargin.trim
    Single("be.doeraene", "scalajs-jquery", "0.9.3", Nel.of("0.9.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: ignore '-core' suffix") {
    val original = """val specs2Version = "4.2.0""""
    val expected = """val specs2Version = "4.3.4""""
    Single("org.specs2", "specs2-core", "4.2.0", Nel.of("4.3.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: use groupId if artifactId is 'core'") {
    val original = """lazy val sttpVersion = "1.3.2""""
    val expected = """lazy val sttpVersion = "1.3.3""""
    Single("com.softwaremill.sttp", "core", "1.3.2", Nel.of("1.3.3"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: version range") {
    val original = """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")"""
    val expected = """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")"""
    Single("org.specs2", "specs2-core", "3.+", Nel.of("4.3.4"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: group with prefix val") {
    val original = """ val circe = "0.10.0-M1" """
    val expected = """ val circe = "0.10.0-M2" """
    Group(
      "io.circe",
      Nel.of("circe-generic", "circe-literal", "circe-parser", "circe-testing"),
      "0.10.0-M1",
      Nel.of("0.10.0-M2")
    ).replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: group with repeated version") {
    val original =
      """ "com.pepegar" %% "hammock-core"  % "0.8.1",
        | "com.pepegar" %% "hammock-circe" % "0.8.1"
      """.stripMargin.trim
    val expected =
      """ "com.pepegar" %% "hammock-core"  % "0.8.5",
        | "com.pepegar" %% "hammock-circe" % "0.8.5"
      """.stripMargin.trim
    Group("com.pepegar", Nel.of("hammock-core", "hammock-circe"), "0.8.1", Nel.of("0.8.5"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllInStrict") {
    val original =
      """ "org.http4s" %% "jawn-fs2" % "0.14.0"
        | "org.typelevel" %% "jawn-json4s"  % "0.14.0",
        | "org.typelevel" %% "jawn-play" % "0.14.0"
      """.stripMargin.trim
    val expected =
      """ "org.http4s" %% "jawn-fs2" % "0.14.0"
        | "org.typelevel" %% "jawn-json4s"  % "0.14.1",
        | "org.typelevel" %% "jawn-play" % "0.14.1"
      """.stripMargin.trim
    Group("org.typelevel", Nel.of("jawn-json4s", "jawn-play"), "0.14.0", Nel.of("0.14.1"))
      .replaceAllInStrict(original) shouldBe Some(expected)
  }

  test("replaceAllIn: artifactIds are common suffixes") {
    val original =
      """lazy val scalajsReactVersion = "1.2.3"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    val expected =
      """lazy val scalajsReactVersion = "1.3.1"
        |lazy val logbackVersion = "1.2.3"
      """.stripMargin
    Group("com.github.japgolly.scalajs-react", Nel.of("core", "extra"), "1.2.3", Nel.of("1.3.1"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: longest common prefix with length 1") {
    val original = """ "com.geirsson" % "sbt-ci-release" % "1.2.1" """
    Group(
      "org.scala-sbt",
      Nel.of("sbt-launch", "scripted-plugin", "scripted-sbt"),
      "1.2.1",
      Nel.of("1.2.4")
    ).replaceAllIn(original) shouldBe None
  }

  test("replaceAllIn: ignore previous") {
    val original =
      """val circeVersion = "0.10.0"
        |val previousCirceIterateeVersion = "0.10.0"
      """.stripMargin
    val expected =
      """val circeVersion = "0.10.1"
        |val previousCirceIterateeVersion = "0.10.0"
      """.stripMargin
    Group(
      "io.circe",
      Nel.of("circe-jawn", "circe-testing"),
      "0.10.0",
      Nel.of("0.10.1")
    ).replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: ignore mimaPreviousArtifacts") {
    val original =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.1"
        |mimaPreviousArtifacts := Set("nl.grons" %% "metrics4-scala" % "4.0.1")
      """.stripMargin
    val expected =
      """"io.dropwizard.metrics" % "metrics-core" % "4.0.3"
        |mimaPreviousArtifacts := Set("nl.grons" %% "metrics4-scala" % "4.0.1")
      """.stripMargin
    Group(
      "io.dropwizard.metrics",
      Nel.of("metrics-core", "metrics-healthchecks"),
      "4.0.1",
      Nel.of("4.0.3")
    ).replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: artifactId with dot") {
    val original = """ def plotlyJs = "1.41.3" """
    val expected = """ def plotlyJs = "1.43.2" """
    Single("org.webjars.bower", "plotly.js", "1.41.3", Nel.of("1.43.2"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllIn: val with backticks") {
    val original = """ val `plotly.js` = "1.41.3" """
    val expected = """ val `plotly.js` = "1.43.2" """
    Single("org.webjars.bower", "plotly.js", "1.41.3", Nel.of("1.43.2"))
      .replaceAllIn(original) shouldBe Some(expected)
  }

  test("replaceAllInRelaxed") {
    val original = """lazy val circeVersion = "0.9.3""""
    val expected = """lazy val circeVersion = "0.11.1""""
    Single("io.circe", "circe-generic", "0.9.3", Nel.of("0.11.1"))
      .replaceAllInRelaxed(original) shouldBe Some(expected)
  }

  test("replaceAllInRelaxed: artifactId with underscore") {
    val original = """val scShapelessV = "1.1.6""""
    val expected = """val scShapelessV = "1.1.8""""
    Single("com.github.alexarchambault", "scalacheck-shapeless_1.13", "1.1.6", Nel.of("1.1.8"))
      .replaceAllInRelaxed(original) shouldBe Some(expected)
  }

  test("replaceAllInRelaxed: camel case artifactId") {
    val original = """val hikariVersion = "3.3.0" """
    val expected = """val hikariVersion = "3.4.0" """
    Single("com.zaxxer", "HikariCP", "3.3.0", Nel.of("3.4.0"))
      .replaceAllInRelaxed(original) shouldBe Some(expected)
  }

  test("replaceAllInSliding: mongo from mongodb") {
    val original = """val mongoVersion = "3.7.0" """
    val expected = """val mongoVersion = "3.7.1" """
    Group(
      "org.mongodb",
      Nel.of("mongodb-driver", "mongodb-driver-async", "mongodb-driver-core"),
      "3.7.0",
      Nel.of("3.7.1")
    ).replaceAllInSliding(original) shouldBe Some(expected)
  }

  test("replaceAllInSliding: artifactId with common suffix") {
    val original = """case _ => "1.0.2" """
    Single("co.fs2", "fs2-core", "1.0.2", Nel.of("1.0.4"))
      .replaceAllInSliding(original) shouldBe None
  }

  test("replaceAllInGroupId: word from groupId") {
    val original = """val acolyteVersion = "1.0.49" """
    val expected = """val acolyteVersion = "1.0.51" """
    Single("org.eu.acolyte", "jdbc-driver", "1.0.49", Nel.of("1.0.51"))
      .replaceAllInGroupId(original) shouldBe Some(expected)
  }

  test("replaceAllInGroupId: ignore TLD") {
    val original = """ "com.propensive" %% "contextual" % "1.0.1" """
    Single("com.slamdata", "fs2-gzip", "1.0.1", Nel.of("1.1.1"))
      .replaceAllInGroupId(original) shouldBe None
  }

  test("replaceAllInGroupId: ignore short words") {
    val original = "SBT_VERSION=1.2.7"
    Single("org.scala-sbt", "scripted-plugin", "1.2.7", Nel.of("1.2.8"))
      .replaceAllInGroupId(original) shouldBe None
  }

  test("Group.artifactId") {
    Group(
      "org.http4s",
      Nel.of("http4s-blaze-server", "http4s-circe", "http4s-core", "http4s-dsl"),
      "0.18.16",
      Nel.of("0.18.18")
    ).artifactId shouldBe "http4s-core"
  }

  test("group: 1 update") {
    val updates = List(Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5")))
    Update.group(updates) shouldBe updates
  }

  test("group: 2 updates") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(artifactId = "specs2-scalacheck")
    Update.group(List(update0, update1)) shouldBe List(
      Group("org.specs2", Nel.of("specs2-core", "specs2-scalacheck"), "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("group: 2 updates with different configurations") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(configurations = Some("test"))
    Update.group(List(update0, update1)) shouldBe List(
      Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("group: 3 updates with different configurations") {
    val update0 = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(configurations = Some("test"))
    val update2 = update0.copy(artifactId = "specs2-scalacheck")
    Update.group(List(update0, update1, update2)) shouldBe List(
      Group("org.specs2", Nel.of("specs2-core", "specs2-scalacheck"), "3.9.4", Nel.of("3.9.5"))
    )
  }

  test("Single.show") {
    val update = Single("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"), Some("test"))
    update.show shouldBe "org.specs2:specs2-core:test : 3.9.4 -> 3.9.5"
  }

  test("Group.show") {
    val update =
      Group("org.scala-sbt", Nel.of("sbt-launch", "scripted-plugin"), "1.2.1", Nel.of("1.2.4"))
    update.show shouldBe "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4"
  }
}
