package org.scalasteward.core.edit

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{GroupId, RepoData, Update}
import org.scalasteward.core.mock.MockContext.context.editAlg
import org.scalasteward.core.mock.MockContext.{config, envVars}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.scalafmtBinary
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

class EditAlgTest extends FunSuite {
  private val gitStatus =
    List("git", "status", "--porcelain", "--untracked-files=no", "--ignore-submodules")

  test("applyUpdate") {
    val repo = Repo("edit-alg", "test-1")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
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
        Log("Trying heuristic 'moduleId'"),
        Cmd("read", file1.pathAsString),
        Log("Trying heuristic 'strict'"),
        Cmd("read", file1.pathAsString),
        Log("Trying heuristic 'original'"),
        Cmd("read", file1.pathAsString),
        Cmd("write", file1.pathAsString),
        Cmd(envVars ++ (repoDir.toString :: gitStatus))
      ),
      files = Map(file1 -> """val catsVersion = "1.3.0"""", file2 -> "")
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with scalafmt update") {
    val repo = Repo("edit-alg", "test-2")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.scalameta" % "scalafmt-core" % "2.0.0", Nel.of("2.1.0"))
    val scalafmtConf = repoDir / ".scalafmt.conf"
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
        Log("Trying heuristic 'moduleId'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'strict'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'original'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'relaxed'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'sliding'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'completeGroupId'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'groupId'"),
        Cmd("read", scalafmtConf.pathAsString),
        Log("Trying heuristic 'specific'"),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd("write", scalafmtConf.pathAsString),
        Cmd(envVars ++ (repoDir.toString :: gitStatus)),
        Log("Executing post-update hook for org.scalameta:scalafmt-core"),
        Cmd("VAR1=val1", "VAR2=val2", repoDir.toString, scalafmtBinary, "--non-interactive"),
        Cmd(envVars ++ (repoDir.toString :: gitStatus))
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

  test("apply update to ammonite file") {
    val repo = Repo("edit-alg", "test-3")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val file1 = repoDir / "script.sc"
    val file2 = repoDir / "build.sbt"

    val state = MockState.empty
      .addFiles(
        file1 -> """import $ivy.`org.typelevel::cats-core:1.2.0`, cats.implicits._"""",
        file2 -> """"org.typelevel" %% "cats-core" % "1.2.0""""
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("test", "-f", repoDir.pathAsString),
        Cmd("test", "-f", file2.pathAsString),
        Cmd("read", file2.pathAsString),
        Cmd("test", "-f", file1.pathAsString),
        Cmd("read", file1.pathAsString),
        Log("Trying heuristic 'moduleId'"),
        Cmd("read", file2.pathAsString),
        Cmd("write", file2.pathAsString),
        Cmd("read", file1.pathAsString),
        Cmd("write", file1.pathAsString),
        Cmd(envVars ++ (repoDir.toString :: gitStatus))
      ),
      files = Map(
        file1 -> """import $ivy.`org.typelevel::cats-core:1.3.0`, cats.implicits._"""",
        file2 -> """"org.typelevel" %% "cats-core" % "1.3.0""""
      )
    )

    assertEquals(state, expected)
  }

  test("https://github.com/circe/circe-config/pull/40") {
    val update = Update.Single("com.typesafe" % "config" % "1.3.3", Nel.of("1.3.4"))
    val original = Map(
      "build.sbt" -> """val config = "1.3.3"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    val expected = Map(
      "build.sbt" -> """val config = "1.3.4"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-4"), update, original), expected)
  }

  test("file restriction when sbt update") {
    val update = Update.Single("org.scala-sbt" % "sbt" % "1.1.2", Nel.of("1.2.8"))
    val original = Map(
      "build.properties" -> """sbt.version=1.1.2""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    val expected = Map(
      "build.properties" -> """sbt.version=1.2.8""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-5"), update, original), expected)
  }

  test("keyword with extra underscore") {
    val update = Update.Group(
      "org.scala-js" % Nel.of("sbt-scalajs", "scalajs-compiler") % "1.1.0",
      Nel.of("1.1.1")
    )
    val original = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.0""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.0")"""
    )
    val expected = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.1""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.1")"""
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-6"), update, original), expected)
  }

  test("test updating group id and version") {
    val update = Update.Single(
      crossDependency = "com.github.mpilquist" % "simulacrum" % "0.19.0",
      newerVersions = Nel.of("1.0.0"),
      newerGroupId = Some(GroupId("org.typelevel")),
      newerArtifactId = Some("simulacrum")
    )
    val original = Map(
      "build.sbt" -> """val simulacrum = "0.19.0"
                       |"com.github.mpilquist" %% "simulacrum" % simulacrum
                       |"""".stripMargin
    )
    val expected = Map(
      "build.sbt" -> """val simulacrum = "1.0.0"
                       |"org.typelevel" %% "simulacrum" % simulacrum
                       |"""".stripMargin
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-7"), update, original), expected)
  }

  test("test updating artifact id and version") {
    val update = Update.Single(
      crossDependency = "com.test" % "artifact" % "1.0.0",
      newerVersions = Nel.of("2.0.0"),
      newerGroupId = Some(GroupId("com.test")),
      newerArtifactId = Some("newer-artifact")
    )
    val original = Map(
      "Dependencies.scala" -> """val testVersion = "1.0.0"
                                |val test = "com.test" %% "artifact" % testVersion
                                |"""".stripMargin
    )
    val expected = Map(
      "Dependencies.scala" -> """val testVersion = "2.0.0"
                                |val test = "com.test" %% "newer-artifact" % testVersion
                                |"""".stripMargin
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-8"), update, original), expected)
  }

  test("NOK artifact change: version and groupId/artifactId in different files") {
    val update = Update.Single(
      crossDependency = "io.chrisdavenport" % "log4cats" % "1.1.1",
      newerVersions = Nel.of("1.2.0"),
      newerGroupId = Some(GroupId("org.typelevel"))
    )
    val original = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.1.1" """,
      "build.sbt" -> """ "io.chrisdavenport" %% "log4cats" % log4catsVersion """
    )
    val expected = Map(
      "Dependencies.scala" -> """val log4catsVersion = "1.2.0" """,
      // The groupId should have been changed here.
      "build.sbt" -> """ "io.chrisdavenport" %% "log4cats" % log4catsVersion """
    )
    assertEquals(runApplyUpdate(Repo("edit-alg", "test-9"), update, original), expected)
  }

  private def runApplyUpdate(
      repo: Repo,
      update: Update,
      files: Map[String, String]
  ): Map[String, String] = {
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = config.workspace / repo.show
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    MockState.empty
      .addFiles(filesInRepoDir.toSeq: _*)
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
  }
}
