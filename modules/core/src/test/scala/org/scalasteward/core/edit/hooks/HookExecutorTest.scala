package org.scalasteward.core.edit.hooks

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.mock.MockContext.{config, hookExecutor, workspaceAlg}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.{RepoConfig, ScalafmtConfig}
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtGroupId}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HookExecutorTest extends AnyFunSuite with Matchers {
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val repoDir = workspaceAlg.repoDir(repo).runA(MockState.empty).unsafeRunSync()
  private val envVars = List(s"GIT_ASKPASS=${config.gitAskPass}", "VAR1=val1", "VAR2=val2")

  test("no hook") {
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val state = hookExecutor
      .execPostUpdateHooks(repo, RepoConfig.empty, update)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty
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
    val state = hookExecutor
      .execPostUpdateHooks(repo, RepoConfig.empty, update)
      .runS(initial)
      .unsafeRunSync()

    state shouldBe initial.copy(
      commands = Vector(
        List(
          "VAR1=val1",
          "VAR2=val2",
          repoDir.toString,
          "scalafmt",
          "--non-interactive"
        ),
        envVars ++ List(
          repoDir.toString,
          "git",
          "status",
          "--porcelain",
          "--untracked-files=no",
          "--ignore-submodules"
        ),
        envVars ++ List(
          repoDir.toString,
          "git",
          "commit",
          "--all",
          "--no-gpg-sign",
          "-m",
          "Reformat with scalafmt 2.7.5"
        )
      ),
      logs = Vector((None, "Executing post-update hook for org.scalameta:scalafmt-core"))
    )

  }

  test("scalafmt: disabled by config") {
    val repoConfig =
      RepoConfig.empty.copy(scalafmt = ScalafmtConfig(runAfterUpgrading = Some(false)))
    val update =
      Update.Single(scalafmtGroupId.value % scalafmtArtifactId % "2.7.4", Nel.of("2.7.5"))
    val state = hookExecutor
      .execPostUpdateHooks(repo, repoConfig, update)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty
  }

  test("sbt-github-actions") {
    val update = Update.Single("com.codecommit" % "sbt-github-actions" % "0.9.4", Nel.of("0.9.5"))
    val state = hookExecutor
      .execPostUpdateHooks(repo, RepoConfig.empty, update)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "githubWorkflowGenerate"
        ),
        envVars ++ List(
          repoDir.toString,
          "git",
          "status",
          "--porcelain",
          "--untracked-files=no",
          "--ignore-submodules"
        )
      ),
      logs = Vector((None, "Executing post-update hook for com.codecommit:sbt-github-actions"))
    )
  }
}
