package org.scalasteward.core.github

import org.http4s.Uri
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data.{Repo, RepoOut, UserOut}
import org.scalasteward.core.mock.MockContext.{config, gitAlg, gitHubRepoAlg}
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalatest.{FunSuite, Matchers}

class GitHubRepoAlgTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString

  val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    Uri.uri("https://github.com/fthomas/datapackage"),
    Branch("master")
  )

  val forkRepoOut = RepoOut(
    "datapackage",
    UserOut("scalasteward"),
    Some(parentRepoOut),
    Uri.uri("https://github.com/scala-steward/datapackage"),
    Branch("master")
  )

  test("clone") {
    val state = gitHubRepoAlg.clone(repo, forkRepoOut).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          config.workspace.toString,
          "git",
          "clone",
          "--recursive",
          s"https://${config.gitHubLogin}@github.com/scala-steward/datapackage",
          repoDir.toString
        ),
        List(repoDir, "git", "config", "user.email", "bot@example.org"),
        List(repoDir, "git", "config", "user.name", "Bot Doe")
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString))
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
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          s"https://${config.gitHubLogin}@github.com/fthomas/datapackage"
        ),
        List(repoDir, "git", "fetch", "upstream"),
        List(repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        List(repoDir, "git", "merge", "upstream/master"),
        List(repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString))
      )
    )
    result shouldBe parentRepoOut
  }

  test("syncFork should do nothing when doNotFork = true") {
    val (state, repoOut) =
      GitHubRepoAlg
        .create(MockContext.config.copy(doNotFork = true), gitAlg)
        .syncFork(repo, parentRepoOut)
        .run(MockState.empty)
        .unsafeRunSync()

    state shouldBe MockState.empty
    repoOut shouldBe parentRepoOut
  }
}
