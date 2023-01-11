package org.scalasteward.core.edit

import better.files.File
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.forge.data.Repo
import org.scalasteward.core.mock.MockConfig.gitCmd
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.ScalafmtAlg.opts
import org.scalasteward.core.scalafmt.{scalafmtBinary, scalafmtConfName, scalafmtDependency}

class EditAlgTest extends FunSuite {
  private def gitStatus(repoDir: File): List[String] =
    gitCmd(repoDir) ++ List("status", "--porcelain", "--untracked-files=no", "--ignore-submodules")

  test("applyUpdate") {
    val repo = Repo("edit-alg", "test-1")
    val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
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
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
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
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
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
}
