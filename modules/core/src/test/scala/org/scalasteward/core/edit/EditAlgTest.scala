package org.scalasteward.core.edit

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data.*
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockEffOps, MockState}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.ScalafmtAlg.opts
import org.scalasteward.core.scalafmt.{scalafmtBinary, scalafmtConfName, scalafmtDependency}

class EditAlgTest extends FunSuite {
  test("applyUpdate") {
    val repo = Repo("edit-alg", "test-1")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val buildSbt = repoDir / "build.sbt"
    val scalaFile = repoDir / "project/Dependencies.scala"
    val gitignore = repoDir / ".gitignore"

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(
        repoDir,
        buildSbt -> """val catsVersion = "1.2.0"""",
        scalaFile -> "",
        gitignore -> ""
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      execCommands = true,
      trace = Vector(
        Cmd.gitGrep(repoDir, update.currentVersion.value),
        Cmd("read", buildSbt.pathAsString),
        Cmd.gitGrep(repoDir, update.groupId.value),
        Cmd("read", buildSbt.pathAsString),
        Cmd("write", buildSbt.pathAsString),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(repoDir, "Update cats-core to 1.3.0"),
        Cmd.gitLatestSha1(repoDir)
      ),
      files = Map(buildSbt -> """val catsVersion = "1.3.0"""", scalaFile -> "", gitignore -> "")
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with scalafmt update") {
    val repo = Repo("edit-alg", "test-2")
    val cache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(scalafmtDependency(Version("2.0.0")), Nil)).withMavenCentral)
    )
    val data = RepoData(repo, cache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val update = ("org.scalameta".g % "scalafmt-core".a % "2.0.0" %> "2.1.0").single
    val gitignore = (repoDir / ".gitignore") -> "target/"
    val scalafmtConf = repoDir / scalafmtConfName
    val scalafmtConfContent = """maxColumn = 100
                                |version = 2.0.0
                                |align.openParenCallSite = false
                                |""".stripMargin
    val buildSbt = (repoDir / "build.sbt") -> "\n"
    val target = repoDir / "target"
    // this file should not be read because it's under target which is git ignored
    val targetScalaFile = target / "SomeFile.scala"

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(repoDir, scalafmtConf -> scalafmtConfContent, buildSbt, gitignore)
      .flatMap(_.addFiles(targetScalaFile -> s""" object Test {"2.0.0"} """))
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      execCommands = true,
      trace = Vector(
        Cmd.gitGrep(repoDir, update.currentVersion.value),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd.gitGrep(repoDir, update.groupId.value),
        Cmd("read", scalafmtConf.pathAsString),
        Cmd("write", scalafmtConf.pathAsString),
        Cmd.exec(repoDir, (scalafmtBinary :: opts.nonInteractive :: opts.modeChanged)*),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(repoDir, "Update scalafmt-core to 2.1.0"),
        Cmd.gitLatestSha1(repoDir),
        Log(
          "Executing post-update hook for org.scalameta:scalafmt-core with command 'scalafmt --non-interactive'"
        ),
        Cmd.exec(repoDir, scalafmtBinary, opts.nonInteractive),
        Cmd.gitStatus(repoDir)
      ),
      files = Map(
        scalafmtConf ->
          """maxColumn = 100
            |version = 2.1.0
            |align.openParenCallSite = false
            |""".stripMargin,
        buildSbt,
        gitignore,
        targetScalaFile -> s"""object Test { "2.0.0" }\n"""
      )
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with build Scalafix") {
    val repo = Repo("edit-alg", "test-3")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val update = (sbtGroupId % sbtArtifactId % "1.4.9" %> "1.5.5").single

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(
        repoDir,
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

  test("applyUpdate for scala-cli using lib") {
    val repo = Repo("edit-alg", "test-4")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val mainSc = repoDir / "main.sc"
    val update = ("org.typelevel".g % "cats-effect".a % "3.5.2" %> "3.5.4").single

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(
        repoDir,
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using lib "org.typelevel::cats-effect:3.5.2"
            |println("Hello!")
            |""".stripMargin
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      execCommands = true,
      trace = Vector(
        Cmd.gitGrep(repoDir, update.currentVersion.value),
        Cmd("read", mainSc.pathAsString),
        Cmd.gitGrep(repoDir, update.groupId.value),
        Cmd("read", mainSc.pathAsString),
        Cmd("read", mainSc.pathAsString),
        Cmd("write", mainSc.pathAsString),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(repoDir, "Update cats-effect to 3.5.4"),
        Cmd.gitLatestSha1(repoDir)
      ),
      files = Map(
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using lib "org.typelevel::cats-effect:3.5.4"
            |println("Hello!")
            |""".stripMargin
      )
    )

    assertEquals(state, expected)
  }

  test("applyUpdate for scala-cli using dep") {
    val repo = Repo("edit-alg", "test-5")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val mainSc = repoDir / "main.sc"
    val update = ("org.typelevel".g % "cats-effect".a % "3.5.2" %> "3.5.4").single

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(
        repoDir,
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using dep "org.typelevel::cats-effect:3.5.2"
            |//> using test.dep "org.scalameta::munit:1.0.0"
            |println("Hello!")
            |""".stripMargin
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      execCommands = true,
      trace = Vector(
        Cmd.gitGrep(repoDir, update.currentVersion.value),
        Cmd("read", mainSc.pathAsString),
        Cmd.gitGrep(repoDir, update.groupId.value),
        Cmd("read", mainSc.pathAsString),
        Cmd("read", mainSc.pathAsString),
        Cmd("write", mainSc.pathAsString),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(repoDir, "Update cats-effect to 3.5.4"),
        Cmd.gitLatestSha1(repoDir)
      ),
      files = Map(
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using dep "org.typelevel::cats-effect:3.5.4"
            |//> using test.dep "org.scalameta::munit:1.0.0"
            |println("Hello!")
            |""".stripMargin
      )
    )

    assertEquals(state, expected)
  }

  test("applyUpdate for scala-cli using test.dep") {
    val repo = Repo("edit-alg", "test-6")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val mainSc = repoDir / "main.sc"
    val update = ("org.scalameta".g % "munit".a % "0.7.29" %> "1.0.0").single

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(
        repoDir,
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using dep "org.typelevel::cats-effect:3.5.4"
            |//> using test.dep "org.scalameta::munit:0.7.29"
            |println("Hello!")
            |""".stripMargin
      )
      .flatMap(editAlg.applyUpdate(data, update).runS)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      execCommands = true,
      trace = Vector(
        Cmd.gitGrep(repoDir, update.currentVersion.value),
        Cmd("read", mainSc.pathAsString),
        Cmd.gitGrep(repoDir, update.groupId.value),
        Cmd("read", mainSc.pathAsString),
        Cmd("read", mainSc.pathAsString),
        Cmd("write", mainSc.pathAsString),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(repoDir, "Update munit to 1.0.0"),
        Cmd.gitLatestSha1(repoDir)
      ),
      files = Map(
        mainSc ->
          """
            |//> using scala "3.4.2"
            |//> using jvm "temurin:21"
            |//> using dep "org.typelevel::cats-effect:3.5.4"
            |//> using test.dep "org.scalameta::munit:1.0.0"
            |println("Hello!")
            |""".stripMargin
      )
    )

    assertEquals(state, expected)
  }
  // Related to https://github.com/scala-steward-org/scala-steward/issues/3780 and
  // https://github.com/scala-steward-org/scala-steward/issues/2906: when several
  // dependencies share a version val, the first update rewrites the val so later
  // updates should not log a misleading "Unable to bump version" warning.
  test("applyUpdate does not warn when version was already bumped via shared val") {
    val repo = Repo("edit-alg", "test-already-bumped")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val buildSbt = repoDir / "build.sbt"
    // Different groupIds so these are not merged by Update.groupByGroupId (see #3780).
    val updateLibrary1 = ("com.foo.core".g % "library-1".a % "1.2.3" %> "1.3.0").single
    val updateLibrary2 = ("com.foo.extra".g % "library-2".a % "1.2.3" %> "1.3.0").single
    val original =
      """val FooLibraryVersion = "1.2.3"
        |libraryDependencies ++= Seq(
        |  "com.foo.core" %% "library-1" % FooLibraryVersion,
        |  "com.foo.extra" %% "library-2" % FooLibraryVersion
        |)
        |""".stripMargin

    val state = MockState.empty
      .copy(execCommands = true)
      .initGitRepo(repoDir, buildSbt -> original)
      .flatMap { s =>
        editAlg.applyUpdate(data, updateLibrary1).runS(s).flatMap { s1 =>
          editAlg.applyUpdate(data, updateLibrary2).runS(s1)
        }
      }
      .unsafeRunSync()

    val warnLogs = state.trace.collect {
      case Log((_, msg)) if msg.contains("Unable to bump version") => msg
    }
    val infoLogs = state.trace.collect {
      case Log((_, msg)) if msg.contains("Version already bumped") => msg
    }
    assertEquals(warnLogs, Vector.empty)
    assert(infoLogs.exists(_.contains("library-2")))
    assertEquals(
      state.files(buildSbt),
      """val FooLibraryVersion = "1.3.0"
        |libraryDependencies ++= Seq(
        |  "com.foo.core" %% "library-1" % FooLibraryVersion,
        |  "com.foo.extra" %% "library-2" % FooLibraryVersion
        |)
        |""".stripMargin
    )
  }


}
