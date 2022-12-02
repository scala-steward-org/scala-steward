package org.scalasteward.core.edit

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update

class FooTest extends FunSuite {
  test("sbt: build.properties") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.3.0-RC1" %> "1.3.0").single
    val original = List("build.properties" -> """sbt.version=1.3.0-RC1""")
    val expected = List("build.properties" -> """sbt.version=1.3.0""")
    val obtained = bar(update, original)
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
    val obtained = bar(update, original)
    assertEquals(obtained, expected)
  }

  test("version range") {
    val update = ("org.specs2".g % "specs2-core".a % "3.+" %> "4.3.4").single
    val original = List("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "3.+" % "test")""")
    val expected = List("build.sbt" -> """Seq("org.specs2" %% "specs2-core" % "4.3.4" % "test")""")
    val obtained = bar(update, original)
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
    val obtained = bar(update, original)
    assertEquals(obtained, expected)
  }

  def bar(update: Update.Single, input: List[(String, String)]): List[(String, String)] = {
    val dependencies = update.on(_.dependencies.toList, _.updates.flatMap(_.dependencies.toList))
    val next = update.on(_.nextVersion, _.updates.head.nextVersion)
    input.map { case (path, content) =>
      val xs = dependencies
        .flatMap(d => VersionScanner.findVersionPositions(d, content))
        .filterNot(_.isCommented)

      path -> xs.headOption.fold(content)(p => p.filePosition.replaceIn(content, next.value))
    }
  }

}
