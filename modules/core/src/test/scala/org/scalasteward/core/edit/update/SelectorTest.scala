package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.update.data.UpdatePositions
import org.scalasteward.core.util.Nel

class SelectorTest extends FunSuite {
  test("sbt plugins") {
    val update = ("org.scala-js".g % "sbt-scalajs".a % "0.6.24" %> "0.6.25").single
    val original = List(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    val expected = List(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("sbt: build.properties") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.3.0-RC1" %> "1.3.0").single
    val original = List("build.properties" -> """sbt.version=1.3.0-RC1""")
    val expected = List("build.properties" -> """sbt.version=1.3.0""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("basic mill") {
    val update = ("com.lihaoyi".g % "requests".a % "0.7.0" %> "0.7.1").single
    val original = List("build.sc" -> """ivy"com.lihaoyi::requests:0.7.0"""")
    val expected = List("build.sc" -> """ivy"com.lihaoyi::requests:0.7.1"""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("$ivy import and sbt ModuleID") {
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val original = List(
      "script.sc" -> """import $ivy.`org.typelevel::cats-core:1.2.0`, cats.implicits._""",
      "build.sbt" -> """"org.typelevel" %% "cats-core" % "1.2.0""""
    )
    val expected = List(
      "script.sc" -> """import $ivy.`org.typelevel::cats-core:1.3.0`, cats.implicits._""",
      "build.sbt" -> """"org.typelevel" %% "cats-core" % "1.3.0""""
    )
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("version range") {
    val update = ("org.specs2".g % "specs2-core".a % "3.+" %> "4.3.4").single
    val original = List("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")""")
    val expected = List("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("commented val") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("Versions.scala" -> """// val scalajsJqueryVersion = "0.9.3"
                                              |val scalajsJqueryVersion = "0.9.3" //bla
                                              |"""".stripMargin.trim)
    val expected = List("Versions.scala" -> """// val scalajsJqueryVersion = "0.9.3"
                                              |val scalajsJqueryVersion = "0.9.4" //bla
                                              |"""".stripMargin.trim)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("ignore hyphen in artifactId") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("Version.scala" -> """val scalajsJqueryVersion = "0.9.3"""")
    val expected = List("Version.scala" -> """val scalajsJqueryVersion = "0.9.4"""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("issue 1314: unrelated ModuleID with same version number, 2") {
    val update = ("org.scalameta".g % "sbt-scalafmt".a % "2.0.1" %> "2.0.7").single
    val original = List("plugins.sbt" -> """val scalafmt = "2.0.1"
                                           |addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
                                           |""".stripMargin)
    val expected = List("plugins.sbt" -> """val scalafmt = "2.0.7"
                                           |addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
                                           |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("ignore 'previous' prefix") {
    val update =
      ("io.circe".g % Nel.of("circe-jawn".a, "circe-testing".a) % "0.10.0" %> "0.10.1").group
    val original = List("build.sbt" -> """val circeVersion = "0.10.0"
                                         |val previousCirceIterateeVersion = "0.10.0"
                                         |""".stripMargin)
    val expected = List("build.sbt" -> """val circeVersion = "0.10.1"
                                         |val previousCirceIterateeVersion = "0.10.0"
                                         |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  private def rewrite(
      update: Update.Single,
      input: List[(String, String)]
  ): List[(String, String)] = {
    // scan
    val versionPositions = input.map { case (path, content) =>
      path -> Scanner.findVersionPositions(update.currentVersion, content)
    }

    // select
    val updatePositions = Selector.select(update, UpdatePositions(versionPositions, List.empty))
    // println(updatePositions)

    // write
    input.map { case (path, content) =>
      val versionPositions = updatePositions.versionPositions
        .find { case (p, _) => p == path }
        .map { case (_, ps) => ps }
        .getOrElse(List.empty)
        .sortBy(_.filePosition.start)
        .reverse

      val updated = versionPositions.foldLeft(content) { (c, pos) =>
        pos.filePosition.replaceIn(c, update.nextVersion.value)
      }
      path -> updated
    }
  }
}
