package org.scalasteward.core.vcs

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context.{gitAlg, logger, vcsRepoAlg}
import org.scalasteward.core.mock.MockContext.{config, envVars}
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockContext, MockEff, MockState}
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}

class VCSRepoAlgTest extends FunSuite {
  private val repo = Repo("fthomas", "datapackage")
  private val repoDir = (config.workspace / "fthomas/datapackage").toString
  private val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/datapackage",
    Branch("master")
  )

  private val forkRepoOut = RepoOut(
    "datapackage",
    UserOut("scala-steward"),
    Some(parentRepoOut),
    uri"https://github.com/scala-steward/datapackage",
    Branch("master")
  )

  private val parentUrl = s"https://${config.vcsLogin}@github.com/fthomas/datapackage"
  private val forkUrl = s"https://${config.vcsLogin}@github.com/scala-steward/datapackage"

  test("cloneAndSync: doNotFork = false") {
    val state = vcsRepoAlg.cloneAndSync(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone scala-steward/datapackage"),
        Cmd(envVars, config.workspace.toString, "git", "clone", forkUrl, repoDir),
        Cmd(envVars, repoDir, "git", "config", "user.email", "bot@example.org"),
        Cmd(envVars, repoDir, "git", "config", "user.name", "Bot Doe"),
        Log("Synchronize with fthomas/datapackage"),
        Cmd(envVars, repoDir, "git", "remote", "add", "upstream", parentUrl),
        Cmd(envVars, repoDir, "git", "fetch", "--force", "--tags", "upstream", "master"),
        Cmd(envVars, repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        Cmd(envVars, repoDir, "git", "merge", "upstream/master"),
        Cmd(envVars, repoDir, "git", "push", "--force", "--set-upstream", "origin", "master"),
        Cmd(envVars, repoDir, "git", "submodule", "update", "--init", "--recursive")
      )
    )
    assertEquals(state, expected)
  }

  test("cloneAndSync: doNotFork = true") {
    val config = MockContext.config.copy(doNotFork = true)
    val state = new VCSRepoAlg[MockEff](config)
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)
      .unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Clone fthomas/datapackage"),
        Cmd(envVars, config.workspace.toString, "git", "clone", parentUrl, repoDir),
        Cmd(envVars, repoDir, "git", "config", "user.email", "bot@example.org"),
        Cmd(envVars, repoDir, "git", "config", "user.name", "Bot Doe"),
        Cmd(envVars, repoDir, "git", "submodule", "update", "--init", "--recursive")
      )
    )
    assertEquals(state, expected)
  }

  test("cloneAndSync: doNotFork = false, no parent") {
    val result = vcsRepoAlg
      .cloneAndSync(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .unsafeRunSync()
    assert(clue(result).isLeft)
  }
}
