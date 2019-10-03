package org.scalasteward.core.github

import org.http4s.Http4sLiteralSyntax
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.{config, gitAlg, gitHubRepoAlg}
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalasteward.core.vcs.VCSRepoAlg
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GitHubRepoAlgTest extends AnyFunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString
  val askPass = s"GIT_ASKPASS=${config.gitAskPass}"

  val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/datapackage",
    Branch("master")
  )

  val forkRepoOut = RepoOut(
    "datapackage",
    UserOut("scalasteward"),
    Some(parentRepoOut),
    uri"https://github.com/scala-steward/datapackage",
    Branch("master")
  )

  test("clone") {
    val state = gitHubRepoAlg.clone(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          askPass,
          config.workspace.toString,
          "git",
          "clone",
          "--recursive",
          s"https://${config.vcsLogin}@github.com/scala-steward/datapackage",
          repoDir.toString
        ),
        List(askPass, repoDir, "git", "config", "user.email", "bot@example.org"),
        List(askPass, repoDir, "git", "config", "user.name", "Bot Doe")
      )
    )
  }

  test("syncFork should throw an exception when doNotFork = false and there is no parent") {
    gitHubRepoAlg
      .syncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync() shouldBe true
  }

  test("syncFork should sync when doNotFork = false and there is a parent") {
    val (state, result) =
      gitHubRepoAlg.syncFork(repo, forkRepoOut).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          askPass,
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          s"https://${config.vcsLogin}@github.com/fthomas/datapackage"
        ),
        List(askPass, repoDir, "git", "fetch", "upstream", "master"),
        List(askPass, repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        List(askPass, repoDir, "git", "merge", "upstream/master"),
        List(askPass, repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
    result shouldBe parentRepoOut
  }

  test("syncFork should do nothing when doNotFork = true") {
    val (state, repoOut) =
      VCSRepoAlg
        .create(MockContext.config.copy(doNotFork = true), gitAlg)
        .syncFork(repo, parentRepoOut)
        .run(MockState.empty)
        .unsafeRunSync()

    state shouldBe MockState.empty
    repoOut shouldBe parentRepoOut
  }
}
