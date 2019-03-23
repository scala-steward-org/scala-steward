package org.scalasteward.core.github

import org.http4s.Uri
import org.scalasteward.core.git.Branch
import org.scalasteward.core.github.data.{Repo, RepoOut, UserOut}
import org.scalasteward.core.mock.MockContext.{config, gitAlg, repoAlg}
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalatest.{FunSuite, Matchers}

class RepoAlgTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString

  val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    Uri.uri("https://github.com/fthomas/datapackage"),
    Branch("master")
  )

  val childRepoOut = RepoOut(
    "datapackage",
    UserOut("scalasteward"),
    Some(parentRepoOut),
    Uri.uri("https://github.com/scala-steward/datapackage"),
    Branch("master")
  )

  test("syncFork should throw an exception when doNotFork = false and there is no parent") {
    repoAlg
      .syncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync() shouldBe true
  }

  test("syncFork should sync when doNotFork = false and there is a parent") {
    val (state, result) = repoAlg.syncFork(repo, childRepoOut).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          s"https://@${config.gitHubLogin}github.com/fthomas/datapackage"
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
    val repoAlg = RepoAlg.create(MockContext.config.copy(doNotFork = true), gitAlg)
    val (state, repoOut) =
      repoAlg.syncFork(repo, parentRepoOut).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty
    repoOut shouldBe parentRepoOut
  }
}
