package org.scalasteward.core.github

import cats.effect.IO
import org.http4s.Uri
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.github.data._
import org.scalasteward.core.mock.MockContext.config
import org.scalatest.{FunSuite, Matchers}

class GithubApiAlgTest extends FunSuite with Matchers {

  val repo = Repo("ChristopherDavenport", "base.g8")

  val parent = RepoOut(
    "base.g8",
    UserOut("ChristopherDavenport"),
    None,
    Uri.uri("https://github.com/ChristopherDavenport/base.g8.git"),
    Branch("master")
  )

  val fork = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parent),
    Uri.uri("https://github.com/scala-steward/base.g8-1.git"),
    Branch("master")
  )

  val defaultBranch = BranchOut(
    Branch("master"),
    CommitOut(Sha1(HexString("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d")))
  )

  val samplePullRequestOut = PullRequestOut(
    Uri.uri("https://github.com/octocat/Hello-World/pull/1347"),
    "open",
    "new-feature"
  )

  val mockGithubAlg: GitHubApiAlg[IO] = new GitHubApiAlg[IO] {
    def createFork(
        repo: Repo
    ): IO[RepoOut] = IO.pure(fork)

    def createPullRequest(repo: Repo, data: NewPullRequestData): IO[PullRequestOut] =
      IO.pure(samplePullRequestOut)

    def getBranch(repo: Repo, branch: Branch): IO[BranchOut] = IO.pure(defaultBranch)

    def getRepo(repo: Repo): IO[RepoOut] =
      if (repo.owner == parent.owner.login) IO.pure(parent)
      else IO.pure(fork)

    def listPullRequests(repo: Repo, head: String): IO[List[PullRequestOut]] =
      IO.pure(List(samplePullRequestOut))
  }

  test("createForkOrGetRepo should create a fork when fork is enabled") {
    mockGithubAlg.createForkOrGetRepo(config, repo).unsafeRunSync() shouldBe fork
  }

  test("createForkOrGetRepo should get the repo info when fork is disabled") {
    mockGithubAlg
      .createForkOrGetRepo(config.copy(doNotFork = true), repo)
      .unsafeRunSync() shouldBe parent
  }

  test(
    "createForkOrGetRepoWithDefaultBranch should fork and get default branch when fork is enabled"
  ) {
    mockGithubAlg
      .createForkOrGetRepoWithDefaultBranch(config, repo)
      .unsafeRunSync() shouldBe ((fork, defaultBranch))
  }

  test(
    "createForkOrGetRepoWithDefaultBranch should just get repo info and default branch info without forking"
  ) {
    mockGithubAlg
      .createForkOrGetRepoWithDefaultBranch(config.copy(doNotFork = true), repo)
      .unsafeRunSync() shouldBe ((parent, defaultBranch))
  }
}
