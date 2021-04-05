package org.scalasteward.core.edit.hooks

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{RepoData, Update}
import org.scalasteward.core.mock.MockConfig.envVars
import org.scalasteward.core.mock.MockContext.context.{hookExecutor, workspaceAlg}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.{RepoConfig, ScalafmtConfig}
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtBinary, scalafmtGroupId}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

class HookExecutorTest extends FunSuite {
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
  private val repoDir = workspaceAlg.repoDir(repo).runA(MockState.empty).unsafeRunSync()

  test("no hook") {

    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty).unsafeRunSync()

    assertEquals(state, MockState.empty)
  }

  test("scalafmt: enabled by config") {
    val update =
      Update.Single(scalafmtGroupId.value % scalafmtArtifactId % "2.7.4", Nel.of("2.7.5"))
    val initial = MockState.empty.copy(commandOutputs =
      Map(
        List("git", "status", "--porcelain", "--untracked-files=no", "--ignore-submodules") ->
          List("build.sbt")
      )
    )
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(initial).unsafeRunSync()

    val expected = initial.copy(
      trace = Vector(
        Log("Executing post-update hook for org.scalameta:scalafmt-core"),
        Cmd("VAR1=val1", "VAR2=val2", repoDir.toString, scalafmtBinary, "--non-interactive"),
        Cmd(
          envVars,
          repoDir.toString,
          "git",
          "status",
          "--porcelain",
          "--untracked-files=no",
          "--ignore-submodules"
        ),
        Cmd(
          envVars,
          repoDir.toString,
          "git",
          "commit",
          "--all",
          "--no-gpg-sign",
          "-m",
          "Reformat with scalafmt 2.7.5"
        )
      )
    )

    assertEquals(state, expected)
  }

  test("scalafmt: disabled by config") {
    val repoConfig =
      RepoConfig.empty.copy(scalafmt = ScalafmtConfig(runAfterUpgrading = Some(false)))
    val data = RepoData(repo, dummyRepoCache, repoConfig)
    val update =
      Update.Single(scalafmtGroupId.value % scalafmtArtifactId % "2.7.4", Nel.of("2.7.5"))
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty).unsafeRunSync()

    assertEquals(state, MockState.empty)
  }

  test("sbt-github-actions") {
    val update = Update.Single("com.codecommit" % "sbt-github-actions" % "0.9.4", Nel.of("0.9.5"))
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty).unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Executing post-update hook for com.codecommit:sbt-github-actions"),
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "githubWorkflowGenerate"
        ),
        Cmd(
          envVars,
          repoDir.toString,
          "git",
          "status",
          "--porcelain",
          "--untracked-files=no",
          "--ignore-submodules"
        )
      )
    )

    assertEquals(state, expected)
  }
}
