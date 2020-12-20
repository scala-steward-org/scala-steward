package org.scalasteward.core.vcs

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.{config, gitAlg, vcsRepoAlg}
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}

class VCSRepoAlgTest extends FunSuite {
  val repo: Repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString
  val envVars = List(s"GIT_ASKPASS=${config.gitAskPass}", "VAR1=val1", "VAR2=val2")

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

  test("clone") {
    val state = vcsRepoAlg.clone(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()
    val url = s"https://${config.vcsLogin}@github.com/scala-steward/datapackage"
    val expected = MockState.empty.copy(
      commands = Vector(
        envVars ++ List(config.workspace.toString, "git", "clone", url, repoDir),
        envVars ++ List(repoDir, "git", "submodule", "update", "--init", "--recursive"),
        envVars ++ List(repoDir, "git", "config", "user.email", "bot@example.org"),
        envVars ++ List(repoDir, "git", "config", "user.name", "Bot Doe")
      )
    )
    assertEquals(state, expected)
  }

  test("syncFork should throw an exception when doNotFork = false and there is no parent") {
    assert(
      vcsRepoAlg
        .syncFork(repo, parentRepoOut)
        .runS(MockState.empty)
        .attempt
        .map(_.isLeft)
        .unsafeRunSync()
    )
  }

  test("syncFork should sync when doNotFork = false and there is a parent") {
    val state = vcsRepoAlg.syncFork(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      commands = Vector(
        envVars ++ List(
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          s"https://${config.vcsLogin}@github.com/fthomas/datapackage"
        ),
        envVars ++ List(repoDir, "git", "fetch", "--tags", "upstream", "master"),
        envVars ++ List(repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        envVars ++ List(repoDir, "git", "merge", "upstream/master"),
        envVars ++ List(repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
    assertEquals(state, expected)
  }

  test("syncFork should do nothing when doNotFork = true") {
    val config = MockContext.config.copy(doNotFork = true)
    val state = VCSRepoAlg
      .create(config)
      .syncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .unsafeRunSync()

    assertEquals(state, MockState.empty)
  }
}
