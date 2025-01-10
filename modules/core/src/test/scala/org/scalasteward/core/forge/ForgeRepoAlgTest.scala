package org.scalasteward.core.forge

import munit.CatsEffectSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.{RepoOut, UserOut}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.{gitAlg, logger, workspaceAlg}
import org.scalasteward.core.mock.MockForgeAuthAlg.noAuth
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockConfig, MockEff, MockEffOps, MockState}

class ForgeRepoAlgTest extends CatsEffectSuite {
  private val repo = Repo("fthomas", "datapackage")
  private val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
  private val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/datapackage",
    Branch("main")
  )

  private val forkRepoOut = RepoOut(
    "datapackage",
    UserOut("scala-steward"),
    Some(parentRepoOut),
    uri"https://github.com/scala-steward/datapackage",
    Branch("main")
  )

  private val parentUrl = "https://github.com/fthomas/datapackage"
  private val forkUrl = "https://github.com/scala-steward/datapackage"

  test("cloneAndSync: doNotFork = false") {
    val obtained =
      new ForgeRepoAlg[MockEff](config).cloneAndSync(repo, forkRepoOut).runS(MockState.empty)
    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone scala-steward/datapackage"),
        Cmd.git(
          config.workspace,
          "clone",
          "-c",
          "clone.defaultRemoteName=origin",
          forkUrl,
          repoDir.toString
        ),
        Cmd.git(repoDir, "config", "user.email", "bot@example.org"),
        Cmd.git(repoDir, "config", "user.name", "Bot Doe"),
        Log("Synchronize with fthomas/datapackage"),
        Cmd.git(repoDir, "remote", "add", "upstream", parentUrl),
        Cmd.git(repoDir, "fetch", "--force", "--tags", "upstream", "main"),
        Cmd.git(repoDir, "checkout", "-B", "main", "--track", "upstream/main"),
        Cmd.git(repoDir, "merge", "upstream/main"),
        Cmd.git(repoDir, "push", "--force", "--set-upstream", "origin", "main"),
        Cmd.git(repoDir, "branch", "--list", "--no-color", "--all", "update"),
        Cmd.git(repoDir, "branch", "--list", "--no-color", "--all", "origin/update"),
        Cmd.git(repoDir, "submodule", "update", "--init", "--recursive")
      )
    )
    obtained.map(assertEquals(_, expected))
  }

  test("cloneAndSync: doNotFork = true") {
    val config =
      MockConfig.config.copy(forgeCfg = MockConfig.config.forgeCfg.copy(doNotFork = true))
    val state = new ForgeRepoAlg[MockEff](config)
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)

    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone fthomas/datapackage"),
        Cmd.git(
          config.workspace,
          "clone",
          "-c",
          "clone.defaultRemoteName=origin",
          parentUrl,
          repoDir.toString
        ),
        Cmd.git(repoDir, "config", "user.email", "bot@example.org"),
        Cmd.git(repoDir, "config", "user.name", "Bot Doe"),
        Cmd.git(repoDir, "submodule", "update", "--init", "--recursive")
      )
    )
    state.map(assertEquals(_, expected))
  }

  test("cloneAndSync: doNotFork = false, no parent") {
    new ForgeRepoAlg[MockEff](config)
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .map(result => assert(clue(result).isLeft))
  }
}
