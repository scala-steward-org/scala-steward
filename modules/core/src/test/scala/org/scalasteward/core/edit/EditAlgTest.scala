package org.scalasteward.core.edit

import better.files.File
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.mock.MockConfig.{config, gitCmd}
import org.scalasteward.core.mock.MockContext.context.editAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.ScalafmtAlg.opts
import org.scalasteward.core.scalafmt.{scalafmtBinary, scalafmtConfName, scalafmtDependency}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

class EditAlgTest extends FunSuite {
  private def gitStatus(repoDir: File): List[String] =
    gitCmd(repoDir) ++ List("status", "--porcelain", "--untracked-files=no", "--ignore-submodules")

  test("applyUpdate") {
    val repo = Repo("edit-alg", "test-1")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.toPath
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val file1 = repoDir / "build.sbt"
    val file2 = repoDir / "project/Dependencies.scala"

    val state = MockState.empty
      .addFiles(file1 -> """val catsVersion = "1.2.0"""", file2 -> "")
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("test", "-f", repoDir.pathAsString),
        Cmd("test", "-f", file1.pathAsString),
        Cmd("read", file1.pathAsString),
        Cmd("test", "-f", (repoDir / "project").pathAsString),
        Cmd("test", "-f", file2.pathAsString),
        Cmd("read", file2.pathAsString),
        Cmd("test", "-f", repoDir.pathAsString),
        Cmd("test", "-f", file1.pathAsString),
        Cmd("read", file1.pathAsString),
        Cmd("test", "-f", (repoDir / "project").pathAsString),
        Cmd("test", "-f", file2.pathAsString),
        Cmd("read", file2.pathAsString),
        Cmd("read", file1.pathAsString),
        Cmd("write", file1.pathAsString),
        Cmd(gitStatus(repoDir))
      ),
      files = Map(file1 -> """val catsVersion = "1.3.0"""", file2 -> "")
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with scalafmt update") {
    val repo = Repo("edit-alg", "test-2")
    val cache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(scalafmtDependency(Version("2.0.0")), Nil)).withMavenCentral)
    )
    val data = RepoData(repo, cache, RepoConfig.empty)
    val repoDir = config.workspace / repo.toPath
    val update = ("org.scalameta".g % "scalafmt-core".a % "2.0.0" %> "2.1.0").single
    val scalafmtConf = repoDir / scalafmtConfName
    val scalafmtConfContent = """maxColumn = 100
                                |version = 2.0.0
                                |align.openParenCallSite = false
                                |""".stripMargin
    val buildSbt = repoDir / "build.sbt"

    val state = MockState.empty
      .addFiles(scalafmtConf -> scalafmtConfContent, buildSbt -> "")
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("test", "-f", repoDir.pathAsString),
        Cmd("test", "-f", scalafmtConf.pathAsString),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd("test", "-f", buildSbt.pathAsString),
        Cmd("read", buildSbt.pathAsString),
        Cmd("test", "-f", repoDir.pathAsString),
        Cmd("test", "-f", scalafmtConf.pathAsString),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd("test", "-f", buildSbt.pathAsString),
        Cmd("read", buildSbt.pathAsString),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd("write", scalafmtConf.pathAsString),
        Cmd(
          "VAR1=val1" :: "VAR2=val2" :: repoDir.toString :: scalafmtBinary :: opts.nonInteractive :: opts.modeChanged
        ),
        Cmd(gitStatus(repoDir)),
        Log(
          "Executing post-update hook for org.scalameta:scalafmt-core with command 'scalafmt --non-interactive'"
        ),
        Cmd(
          "VAR1=val1" :: "VAR2=val2" :: repoDir.toString :: scalafmtBinary :: opts.nonInteractive :: Nil
        ),
        Cmd(gitStatus(repoDir))
      ),
      files = Map(
        scalafmtConf ->
          """maxColumn = 100
            |version = 2.1.0
            |align.openParenCallSite = false
            |""".stripMargin,
        buildSbt -> ""
      )
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with build Scalafix") {
    val repo = Repo("edit-alg", "test-3-1")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.toPath
    val update = (sbtGroupId % sbtArtifactId % "1.4.9" %> "1.5.5").single

    val state = MockState.empty
      .addFiles(
        repoDir / "build.sbt" -> "",
        repoDir / "project" / "build.properties" -> """sbt.version=1.4.9"""
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    assert(state.trace.exists {
      case Cmd(cmd) =>
        cmd.contains(scalafixBinary) && cmd.exists(_.contains("Sbt0_13BuildSyntax.scala"))
      case Log(_) => false
    })
  }

  // https://github.com/circe/circe-config/pull/40
  test("typesafe config update and sbt-site with the same version") {
    val update = ("com.typesafe".g % "config".a % "1.3.3" %> "1.3.4").single
    val original = Map(
      "build.sbt" -> """val config = "1.3.3"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    val expected = Map(
      "build.sbt" -> """val config = "1.3.4"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("file restriction when sbt update") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.1.2" %> "1.2.8").single
    val original = Map(
      "build.properties" -> """sbt.version=1.1.2""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    val expected = Map(
      "build.properties" -> """sbt.version=1.2.8""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("keyword with extra underscore") {
    val update =
      ("org.scala-js".g % Nel.of("sbt-scalajs".a, "scalajs-compiler".a) % "1.1.0" %> "1.1.1").group
    val original = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.0""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.0")"""
    )
    val expected = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.1""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.1")"""
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  /*
  test("mill version file update") {
    val update = ("com.lihaoyi".g % "mill-main".a % "0.9.5" %> "0.9.9").single
    val original = Map(
      ".mill-version" -> "0.9.5 \n ",
      ".travis.yml" -> """- TEST_MILL_VERSION=0.9.5"""
    )
    val expected = Map(
      ".mill-version" -> "0.9.9 \n ",
      ".travis.yml" -> """- TEST_MILL_VERSION=0.9.5"""
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-10"), update, original), expected)
  }
   */

  test("disable updates on single lines with 'off' (no 'on')") {
    val update =
      ("com.typesafe.akka".g % Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    val original =
      Map("build.sbt" -> """ "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
                           | "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
                           | """.stripMargin)
    val expected =
      Map("build.sbt" -> """ "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
                           | "com.typesafe.akka" %% "akka-testkit" % "2.5.0",
                           | """.stripMargin)
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("disable updates on multiple lines after 'off' (no 'on')") {
    val update =
      ("com.typesafe.akka".g % Nel.of("akka-actor".a, "akka-testkit".a) % "2.4.0" %> "2.5.0").group
    val original = Map("build.sbt" -> """ // scala-steward:off
                                        | "com.typesafe.akka" %% "akka-actor" % "2.4.0",
                                        | "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
                                        | """.stripMargin)
    val expected = original
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("hash before 'off'") {
    val update = ("org.scala-sbt".g % "sbt".a % "1.2.8" %> "1.4.3").single
    val original = Map("build.properties" -> """# scala-steward:off
                                               |sbt.version=1.2.8
                                               |""".stripMargin)
    val expected = original
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("update multiple lines between 'on' and 'off'") {
    val update = ("com.typesafe.akka".g %
      Nel.of("akka-actor".a, "akka-testkit".a, "akka-slf4j".a) % "2.4.20" %> "2.5.0").group
    val original =
      Map("build.sbt" -> """  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
                           |  // scala-steward:on
                           |  "com.typesafe.akka" %% "akka-slf4j" % "2.4.20" % "test"
                           |  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
                           |  """.stripMargin.trim)
    val expected =
      Map("build.sbt" -> """  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
                           |  // scala-steward:on
                           |  "com.typesafe.akka" %% "akka-slf4j" % "2.5.0" % "test"
                           |  // scala-steward:off
                           |  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test"
                           |  """.stripMargin.trim)
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("match artifactId cross name in Maven dependency") {
    val update =
      ("io.chrisdavenport".g % ("log4cats", "log4cats_2.13").a % "1.1.1" %> "1.2.0").single
    val original = Map("pom.xml" -> """<groupId>io.chrisdavenport</groupId>
                                      |<artifactId>log4cats_2.13</artifactId>
                                      |<version>1.1.1</version>""".stripMargin)
    val expected = Map("pom.xml" -> """<groupId>io.chrisdavenport</groupId>
                                      |<artifactId>log4cats_2.13</artifactId>
                                      |<version>1.2.0</version>""".stripMargin)
    assertEquals(runApplyUpdate(update, original), expected)
  }

  private var counter = 0
  private def nextInt(): Int = {
    counter = counter + 1
    counter
  }

  private def runApplyUpdate(
      update: Update.Single,
      files: Map[String, String]
  ): Map[String, String] = {
    val repo = Repo("edit-alg", s"runApplyUpdate-${nextInt()}")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.toPath
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    MockState.empty
      .addFiles(filesInRepoDir.toSeq: _*)
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
  }
}
