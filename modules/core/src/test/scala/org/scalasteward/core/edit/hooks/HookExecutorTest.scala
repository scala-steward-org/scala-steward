package org.scalasteward.core.edit.hooks

import cats.data.NonEmptyList
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.scalasteward.core.TestInstances.{dummyRepoCache, dummySha1}
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.{Repo, RepoData}
import org.scalasteward.core.git.gitBlameIgnoreRevsName
import org.scalasteward.core.io.process.ProcessFailedException
import org.scalasteward.core.io.{process, FileAlgTest}
import org.scalasteward.core.mock.MockContext.context.{hookExecutor, workspaceAlg}
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockEffOps, MockState}
import org.scalasteward.core.repoconfig.{PostUpdateHookConfig, RepoConfig, ScalafmtConfig}
import org.scalasteward.core.scalafmt.ScalafmtAlg.opts
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtBinary, scalafmtGroupId}
import org.scalasteward.core.util.Nel
import scala.collection.mutable.ListBuffer

class HookExecutorTest extends CatsEffectSuite {
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val data = RepoData(repo, dummyRepoCache, RepoConfig.empty)
  private val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()

  test("no hook") {
    val update = ("org.typelevel".g % "cats-core".a % "1.2.0" %> "1.3.0").single
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty)
    state.map(assertEquals(_, MockState.empty))
  }

  Seq(true, false).foreach { blameRevIgnored =>
    val isIgnored = if (blameRevIgnored) "ignored" else "not ignored"

    test(s"scalafmt: enabled by config / $gitBlameIgnoreRevsName $isIgnored") {
      val gitBlameIgnoreRevs = repoDir / gitBlameIgnoreRevsName
      val update = (scalafmtGroupId % scalafmtArtifactId % "2.7.4" %> "2.7.5").single
      val initial = MockState.empty.copy(commandOutputs =
        Map(
          Cmd.gitStatus(repoDir) -> Right(List("build.sbt")),
          Cmd.gitLatestSha1(repoDir) -> Right(List(dummySha1.value.value)),
          Cmd.git(repoDir, "check-ignore", gitBlameIgnoreRevs.pathAsString) ->
            (if (blameRevIgnored) Right(List.empty) else Left(dummyProcessError))
        )
      )
      val state = FileAlgTest.ioFileAlg.deleteForce(gitBlameIgnoreRevs) >>
        hookExecutor.execPostUpdateHooks(data, update).runS(initial)

      val logIfIgnored =
        if (blameRevIgnored)
          Vector(Log(s"Impossible to add '$gitBlameIgnoreRevs' because it is git ignored."))
        else
          Vector(
            Cmd.git(repoDir, "add", gitBlameIgnoreRevs.pathAsString),
            Cmd.gitStatus(repoDir),
            Cmd.gitCommit(
              repoDir,
              s"Add 'Reformat with scalafmt 2.7.5' to $gitBlameIgnoreRevsName"
            ),
            Cmd.gitLatestSha1(repoDir)
          )

      val traces = Vector(
        Log(
          "Executing post-update hook for org.scalameta:scalafmt-core with command 'scalafmt --non-interactive'"
        ),
        Cmd(
          "VAR1=val1" :: "VAR2=val2" :: repoDir.toString :: scalafmtBinary :: opts.nonInteractive :: Nil
        ),
        Cmd.gitStatus(repoDir),
        Cmd.gitCommit(
          repoDir,
          "Reformat with scalafmt 2.7.5",
          s"Executed command: $scalafmtBinary ${opts.nonInteractive}"
        ),
        Cmd.gitLatestSha1(repoDir),
        Cmd("read", gitBlameIgnoreRevs.pathAsString),
        Cmd("write", gitBlameIgnoreRevs.pathAsString),
        Cmd.git(repoDir, "check-ignore", gitBlameIgnoreRevs.pathAsString)
      ) ++
        logIfIgnored ++ Vector(
        )
      val expected = initial.copy(
        trace = traces,
        files = Map(
          gitBlameIgnoreRevs -> s"# Scala Steward: Reformat with scalafmt 2.7.5\n${dummySha1.value.value}\n"
        )
      )

      state.map(assertEquals(_, expected))
    }
  }

  test("scalafmt: disabled by config") {
    val repoConfig =
      RepoConfig.empty.copy(scalafmt = ScalafmtConfig(runAfterUpgrading = Some(false)).some)
    val data = RepoData(repo, dummyRepoCache, repoConfig)
    val update = (scalafmtGroupId % scalafmtArtifactId % "2.7.4" %> "2.7.5").single
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty)

    state.map(assertEquals(_, MockState.empty))
  }

  test("sbt-github-actions") {
    val update = ("com.codecommit".g % "sbt-github-actions".a % "0.9.4" %> "0.9.5").single
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty)

    val expected = MockState.empty.copy(
      trace = Vector(
        Log(
          "Executing post-update hook for com.codecommit:sbt-github-actions with command 'sbt githubWorkflowGenerate'"
        ),
        Cmd.execSandboxed(repoDir, "sbt", "githubWorkflowGenerate"),
        Cmd.gitStatus(repoDir)
      )
    )

    state.map(assertEquals(_, expected))
  }

  test("hook from config") {
    val update = ("com.random".g % "cool-lib".a % "1.0" %> "1.1").single
    val config = RepoConfig(
      postUpdateHooks = List(
        PostUpdateHookConfig(
          groupId = None,
          artifactId = None,
          command = Nel.of("sbt", "mySbtCommand"),
          commitMessage = "Updated with a hook!"
        )
      ).some
    )
    val data = RepoData(repo, dummyRepoCache, config)
    val state = hookExecutor.execPostUpdateHooks(data, update).runS(MockState.empty)

    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Executing post-update hook for com.random:cool-lib with command 'sbt mySbtCommand'"),
        Cmd.execSandboxed(repoDir, "sbt", "mySbtCommand"),
        Cmd.gitStatus(repoDir)
      )
    )

    state.map(assertEquals(_, expected))
  }

  private val dummyProcessError =
    new ProcessFailedException(process.Args(NonEmptyList.of("cmd")), ListBuffer.empty, 1)
}
