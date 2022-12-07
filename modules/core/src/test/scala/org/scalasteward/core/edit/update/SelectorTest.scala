package org.scalasteward.core.edit.update

import cats.syntax.all._
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.Nel

class SelectorTest extends FunSuite {
  test("all on one line") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("build.sbt" -> """"be.doeraene" %% "scalajs-jquery"  % "0.9.3"""")
    val expected = List("build.sbt" -> """"be.doeraene" %% "scalajs-jquery"  % "0.9.4"""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("all upper case val") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("build.sbt" -> """val SCALAJSJQUERYVERSION = "0.9.3"""")
    val expected = List("build.sbt" -> """val SCALAJSJQUERYVERSION = "0.9.4"""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("val with backticks") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("build.sbt" -> """val `scalajs-jquery-version` = "0.9.3"""")
    val expected = List("build.sbt" -> """val `scalajs-jquery-version` = "0.9.4"""")
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

  test("just artifactId without version") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("build.sbt" -> """val scalajsjquery = "0.9.3"""")
    val expected = List("build.sbt" -> """val scalajsjquery = "0.9.4"""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("version val with line break") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("Versions.scala" -> """val scalajsJqueryVersion =
                                              |  "0.9.3"""".stripMargin)
    val expected = List("Versions.scala" -> """val scalajsJqueryVersion =
                                              |  "0.9.4"""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  /* TODO
  test("unrelated val with the same version") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List("Versions.scala" -> """val scalajsJqueryVersion = "0.9.3"
                                              |val fooVersion = "0.9.3"""".stripMargin)
    val expected = List("Versions.scala" -> """val scalajsJqueryVersion = "0.9.4"
                                              |val fooVersion = "0.9.3"""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }
   */

  test("group with repeated version") {
    val update =
      ("com.pepegar".g % Nel.of("hammock-core".a, "hammock-circe".a) % "0.8.1" %> "0.8.5").group
    val original = List("build.sbt" -> """ "com.pepegar" %% "hammock-core"  % "0.8.1",
                                         | "com.pepegar" %% "hammock-circe" % "0.8.1"
                                         |""".stripMargin.trim)
    val expected = List("build.sbt" -> """ "com.pepegar" %% "hammock-core"  % "0.8.5",
                                         | "com.pepegar" %% "hammock-circe" % "0.8.5"
                                         |""".stripMargin.trim)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

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

  test("sbt plugins: missing version") {
    val update = ("org.scala-js".g % "sbt-scalajs".a % "0.6.24" %> "0.6.25").single
    val original = List(
      "plugins.sbt" -> """addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
                         |addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
                         |addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
                         |""".stripMargin.trim
    )
    val expected = original
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

  test("mill with cross") {
    val update = ("com.goyeau".g % "mill-scalafix".a % "0.2.9" %> "0.2.10").single
    val original = List("build.sc" -> """import $ivy.`com.goyeau::mill-scalafix::0.2.9`""")
    val expected = List("build.sc" -> """import $ivy.`com.goyeau::mill-scalafix::0.2.10`""")
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("$ivy import") {
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val original = List("build.sc" -> " import $ivy.`org.typelevel::cats-core:1.2.0` ")
    val expected = List("build.sc" -> " import $ivy.`org.typelevel::cats-core:1.3.0` ")
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

  test("commented ModuleIDs") {
    val update = ("be.doeraene".g % "scalajs-jquery".a % "0.9.3" %> "0.9.4").single
    val original = List(
      "build.sbt" -> """ "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                       | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                       |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                       |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                       |"""".stripMargin
    )
    val expected = List(
      "build.sbt" -> """ "be.doeraene" %% "scalajs-jquery"  % "0.9.4"
                       | // "be.doeraene" %% "scalajs-jquery"  % "0.9.3"
                       |   addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.4")
                       |   //addSbtPlugin("be.doeraene" %% "scalajs-jquery"  % "0.9.3")
                       |"""".stripMargin
    )
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

  // https://github.com/scala-steward-org/scala-steward/pull/793
  test("similar artifactIds and same version") {
    val update =
      ("org.typelevel".g % Nel.of("cats-core".a, "cats-laws".a) % "2.0.0-M4" %> "2.0.0-RC1").group
    val original =
      List("build.sbt" -> """ "org.typelevel" %%% "cats-core" % "2.0.0-M4",
                            | "org.typelevel" %%% "cats-laws" % "2.0.0-M4" % "test",
                            | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
                            | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
                            |""".stripMargin)
    val expected =
      List("build.sbt" -> """ "org.typelevel" %%% "cats-core" % "2.0.0-RC1",
                            | "org.typelevel" %%% "cats-laws" % "2.0.0-RC1" % "test",
                            | "org.typelevel" %%% "cats-effect" % "2.0.0-M4",
                            | "org.typelevel" %%% "cats-effect-laws" % "2.0.0-M4" % "test",
                            |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("unrelated ModuleID with same version number") {
    val update = ("org.scala-sbt".g %
      Nel.of("sbt-launch".a, "scripted-plugin".a, "scripted-sbt".a) % "1.2.1" %> "1.2.4").group
    val original = List("plugins.sbt" -> """ "com.geirsson" % "sbt-ci-release" % "1.2.1" """)
    val expected = original
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1314
  test("unrelated ModuleID with same version number, 2") {
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

  // https://github.com/scala-steward-org/scala-steward/issues/128
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

  // https://github.com/scala-steward-org/scala-steward/issues/236
  test("same version, same artifact prefix, different groupId") {
    val update =
      ("org.typelevel".g % Nel.of("jawn-json4s".a, "jawn-play".a) % "0.14.0" %> "0.14.1").group
    val original =
      List("build.sbt" -> """ "org.http4s" %% "jawn-fs2" % "0.14.0"
                            | "org.typelevel" %% "jawn-json4s"  % "0.14.0",
                            | "org.typelevel" %% "jawn-play" % "0.14.0" """.stripMargin.trim)
    val expected =
      List("build.sbt" -> """ "org.http4s" %% "jawn-fs2" % "0.14.0"
                            | "org.typelevel" %% "jawn-json4s"  % "0.14.1",
                            | "org.typelevel" %% "jawn-play" % "0.14.1" """.stripMargin.trim)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("val with similar name") {
    val update = ("org.typelevel".g % "cats-core".a % "2.0.0" %> "2.1.0").single
    val original =
      List("Dependencies.scala" -> """val cats = "2.0.0"
                                     |val catsEffect2 = "2.0.0"
                                     |"org.typelevel" %% "cats-core" % cats
                                     |"org.typelevel" %% "cats-effect" % catsEffect2
                                     |""".stripMargin)
    val expected =
      List("Dependencies.scala" -> """val cats = "2.1.0"
                                     |val catsEffect2 = "2.0.0"
                                     |"org.typelevel" %% "cats-core" % cats
                                     |"org.typelevel" %% "cats-effect" % catsEffect2
                                     |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1184
  test("qualified val with similar name") {
    val update = ("org.typelevel".g % "cats-core".a % "2.0.0" %> "2.1.0").single
    val original =
      List("Dependencies.scala" -> """val cats = "2.0.0"
                                     |val catsEffect2 = "2.0.0"
                                     |"org.typelevel" %% "cats-core" % Version.cats
                                     |"org.typelevel" %% "cats-effect" % Version.catsEffect2
                                     |""".stripMargin)
    val expected =
      List("Dependencies.scala" -> """val cats = "2.1.0"
                                     |val catsEffect2 = "2.0.0"
                                     |"org.typelevel" %% "cats-core" % Version.cats
                                     |"org.typelevel" %% "cats-effect" % Version.catsEffect2
                                     |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/502
  test("version that contains the current version as proper substring") {
    val artifactIds = Nel.of("keywords-each".a, "keywords-using".a)
    val update = ("com.thoughtworks.dsl".g % artifactIds % "1.2.0" %> "1.3.0").group
    val original =
      List("build.sbt" -> """"com.thoughtworks.dsl" %%% "keywords-using" % "1.2.0"
                            |"com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd"
                            |""".stripMargin)
    val expected =
      List("build.sbt" -> """"com.thoughtworks.dsl" %%% "keywords-using" % "1.3.0"
                            |"com.thoughtworks.dsl" %%% "keywords-each"  % "1.2.0+14-7a373cbd"
                            |""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/pull/566
  test("prevent exception: named capturing group is missing trailing '}'") {
    val update =
      ("org.nd4j".g % Nel.of("nd4j-api".a, "nd4j-native-platform".a) % "0.8.0" %> "0.9.1").group
    val original = List(
      "build.sbt" -> (""""org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.8.0"""")
    )
    val expected = List(
      "build.sbt" -> (""""org.nd4j" % s"nd4j-""" + "$" + """{nd4jRuntime.value}-platform" % "0.9.1"""")
    )
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("artifact change with sbt ModuleID") {
    val update = ("org.spire-math".g % "kind-projector".a % "0.9.0" %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val original = List("build.sbt" -> """ "org.spire-math" %% "kind-projector" % "0.9.0" """)
    val expected = List("build.sbt" -> """ "org.typelevel" %% "kind-projector" % "0.10.0" """)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("artifact change with Mill dependency") {
    val update = ("org.spire-math".g % "kind-projector".a % "0.9.0" %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val original = List("build.sc" -> """ "org.spire-math::kind-projector:0.9.0" """)
    val expected = List("build.sc" -> """ "org.typelevel::kind-projector:0.10.0" """)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("test updating group id and version") {
    val update = ("com.github.mpilquist".g % "simulacrum".a % "0.19.0" %> "1.0.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("simulacrum"))
    val original = List("build.sbt" -> """val simulacrum = "0.19.0"
                                         |"com.github.mpilquist" %% "simulacrum" % simulacrum
                                         |"""".stripMargin)
    val expected = List("build.sbt" -> """val simulacrum = "1.0.0"
                                         |"org.typelevel" %% "simulacrum" % simulacrum
                                         |"""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  test("test updating artifact id and version") {
    val update = ("com.test".g % "artifact".a % "1.0.0" %> "2.0.0").single
      .copy(newerGroupId = Some("com.test".g), newerArtifactId = Some("newer-artifact"))
    val original =
      List("Dependencies.scala" -> """val testVersion = "1.0.0"
                                     |val test = "com.test" %% "artifact" % testVersion
                                     |"""".stripMargin)
    val expected =
      List("Dependencies.scala" -> """val testVersion = "2.0.0"
                                     |val test = "com.test" %% "newer-artifact" % testVersion
                                     |"""".stripMargin)
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  // https://github.com/scala-steward-org/scala-steward/issues/1977
  test("artifact change: version and groupId/artifactId in different files") {
    val update = ("io.chrisdavenport".g % "log4cats".a % "1.1.1" %> "1.2.0").single
      .copy(newerGroupId = Some("org.typelevel".g))
    val original = List(
      "Dependencies.scala" -> """val log4catsVersion = "1.1.1" """,
      "build.sbt" -> """ "io.chrisdavenport" %% "log4cats" % log4catsVersion """
    )
    val expected = List(
      "Dependencies.scala" -> """val log4catsVersion = "1.2.0" """,
      "build.sbt" -> """ "org.typelevel" %% "log4cats" % log4catsVersion """
    )
    val obtained = rewrite(update, original)
    assertEquals(obtained, expected)
  }

  private def rewrite(
      update: Update.Single,
      input: List[(String, String)]
  ): List[(String, String)] = {
    // scan
    val versionPositions = input.map { case (path, content) =>
      path -> VersionScanner.findPositions(update.currentVersion, content)
    }
    val modulePositions = input.map { case (path, content) =>
      path -> update.dependencies.toList.flatMap(ModuleScanner.findPositions(_, content))
    }

    // select
    val replacements = Selector.select(update, versionPositions, modulePositions)
    // println(replacements)

    // write
    input.map { case (path, content) =>
      val matchingReplacements = replacements
        .collect { case (p, ps) if p === path => ps }
        .flatten
        .sortBy(_.position.start)
        .reverse

      val updated = matchingReplacements.foldLeft(content)((c, r) => r.replaceIn(c))
      path -> updated
    }
  }
}

// com.lihaoyi:mill-scalalib from 0.10.9 to 0.10.10
// val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.10")
// val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.10.9")
// val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
