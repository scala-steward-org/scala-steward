package eu.timepit.scalasteward.model

import eu.timepit.scalasteward.util.Nel
import eu.timepit.scalasteward.model.Update.{Group, Single}
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
    Update.group(List(update0, update1)) shouldBe
      List(
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
