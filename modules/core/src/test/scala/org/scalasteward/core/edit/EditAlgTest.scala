package org.scalasteward.core.edit

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
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
        Cmd.exec(repoDir, scalafmtBinary :: opts.nonInteractive :: opts.modeChanged: _*),
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
}
