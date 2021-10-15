package org.scalasteward.core.vcs

import munit.CatsEffectSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockConfig.{config, gitCmd}
import org.scalasteward.core.mock.MockContext.context.{gitAlg, logger, vcsRepoAlg}
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockConfig, MockEff, MockState}
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}

class VCSRepoAlgTest extends CatsEffectSuite {
  private val repo = Repo("fthomas", "datapackage")
  private val repoDir = config.workspace / repo.show
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

  private val parentUrl = s"https://${config.vcsCfg.login}@github.com/fthomas/datapackage"
  private val forkUrl = s"https://${config.vcsCfg.login}@github.com/scala-steward/datapackage"

  test("cloneAndSync: doNotFork = false") {
    val state = vcsRepoAlg.cloneAndSync(repo, forkRepoOut).runS(MockState.empty)
    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone scala-steward/datapackage"),
        Cmd(gitCmd(config.workspace), "clone", forkUrl, repoDir.toString),
        Cmd(gitCmd(repoDir), "config", "user.email", "bot@example.org"),
        Cmd(gitCmd(repoDir), "config", "user.name", "Bot Doe"),
        Log("Synchronize with fthomas/datapackage"),
        Cmd(gitCmd(repoDir), "remote", "add", "upstream", parentUrl),
        Cmd(gitCmd(repoDir), "fetch", "--force", "--tags", "upstream", "main"),
        Cmd(gitCmd(repoDir), "checkout", "-B", "main", "--track", "upstream/main"),
        Cmd(gitCmd(repoDir), "merge", "upstream/main"),
        Cmd(gitCmd(repoDir), "push", "--force", "--set-upstream", "origin", "main"),
        Cmd(gitCmd(repoDir), "submodule", "update", "--init", "--recursive")
      )
    )
    state.map(assertEquals(_, expected))
  }

  test("cloneAndSync: doNotFork = true") {
    val config = MockConfig.config.copy(vcsCfg = MockConfig.config.vcsCfg.copy(doNotFork = true))
    val state = new VCSRepoAlg[MockEff](config)
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)

    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone fthomas/datapackage"),
        Cmd(gitCmd(config.workspace), "clone", parentUrl, repoDir.toString),
        Cmd(gitCmd(repoDir), "config", "user.email", "bot@example.org"),
        Cmd(gitCmd(repoDir), "config", "user.name", "Bot Doe"),
        Cmd(gitCmd(repoDir), "submodule", "update", "--init", "--recursive")
      )
    )
    state.map(assertEquals(_, expected))
  }

  test("cloneAndSync: doNotFork = false, no parent") {
    vcsRepoAlg
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .map(result => assert(clue(result).isLeft))
  }
}
