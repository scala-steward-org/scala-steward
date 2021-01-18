package org.scalasteward.core.vcs

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context.{gitAlg, logger, vcsRepoAlg}
import org.scalasteward.core.mock.MockContext.{config, envVars}
import org.scalasteward.core.mock.{MockContext, MockEff, MockState}
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}

class VCSRepoAlgTest extends FunSuite {
  val repo: Repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString
  val parentRepoOut: RepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/datapackage",
    Branch("master")
  )

  val forkRepoOut: RepoOut = RepoOut(
    "datapackage",
    UserOut("scalasteward"),
    Some(parentRepoOut),
    uri"https://github.com/scala-steward/datapackage",
    Branch("master")
  )

  test("cloneAndSync") {
    val state = vcsRepoAlg.cloneAndSync(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()
    val url0 = s"https://${config.vcsLogin}@github.com/fthomas/datapackage"
    val url1 = s"https://${config.vcsLogin}@github.com/scala-steward/datapackage"
    val expected = MockState.empty.copy(
      commands = Vector(
        envVars ++ List(config.workspace.toString, "git", "clone", url1, repoDir),
        envVars ++ List(repoDir, "git", "config", "user.email", "bot@example.org"),
        envVars ++ List(repoDir, "git", "config", "user.name", "Bot Doe"),
        envVars ++ List(repoDir, "git", "remote", "add", "upstream", url0),
        envVars ++ List(repoDir, "git", "fetch", "--force", "--tags", "upstream", "master"),
        envVars ++ List(repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        envVars ++ List(repoDir, "git", "merge", "upstream/master"),
        envVars ++ List(repoDir, "git", "push", "--force", "--set-upstream", "origin", "master"),
        envVars ++ List(repoDir, "git", "submodule", "update", "--init", "--recursive")
      ),
      logs = Vector((None, "Clone and synchronize fthomas/datapackage"))
    )
    assertEquals(state, expected)
  }

  test("syncFork should throw an exception when doNotFork = false and there is no parent") {
    val result = vcsRepoAlg
      .syncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .unsafeRunSync()
    assert(clue(result).isLeft)
  }

  test("syncFork should do nothing when doNotFork = true") {
    val config = MockContext.config.copy(doNotFork = true)
    val state = new VCSRepoAlg[MockEff](config)
      .syncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .unsafeRunSync()
    assertEquals(state, MockState.empty)
  }
}
