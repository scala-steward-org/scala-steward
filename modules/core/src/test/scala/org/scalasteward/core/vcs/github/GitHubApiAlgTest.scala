package org.scalasteward.core.vcs.github

import cats.effect.IO
import io.circe.literal._
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._

class GitHubApiAlgTest extends FunSuite {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "repos" / "fthomas" / "base.g8" =>
        Ok(
          json""" {
            "name": "base.g8",
            "owner": { "login": "fthomas" },
            "clone_url": "https://github.com/fthomas/base.g8.git",
            "default_branch": "master"
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "branches" / "master" =>
        Ok(
          json""" {
            "name": "master",
            "commit": { "sha": "07eb2a203e297c8340273950e98b2cab68b560c1" }
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "pulls" =>
        Ok(
          json"""[{
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "state": "open",
            "number": 1347,
            "title": "new-feature"
          }]"""
        )

      case PATCH -> Root / "repos" / "fthomas" / "base.g8" / "pulls" / IntVar(_) =>
        Ok(
          json"""{
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "state": "closed",
            "number": 1347,
            "title": "new-feature"
          }"""
        )

      case POST -> Root / "repos" / "fthomas" / "base.g8" / "forks" =>
        Ok(
          json""" {
            "name": "base.g8-1",
            "owner": { "login": "scala-steward" },
            "parent": {
              "name": "base.g8",
              "owner": { "login": "fthomas" },
              "clone_url": "https://github.com/fthomas/base.g8.git",
              "default_branch": "master"
            },
            "clone_url": "https://github.com/scala-steward/base.g8-1.git",
            "default_branch": "master"
          } """
        )

      case POST -> Root / "repos" / "fthomas" / "base.g8" / "issues" / IntVar(_) / "comments" =>
        Created(json"""  {
            "body": "Superseded by #1234"
          } """)

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  val gitHubApiAlg = new GitHubApiAlg[IO](config.vcsApiHost, _ => IO.pure)

  private val repo = Repo("fthomas", "base.g8")

  private val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/base.g8.git",
    Branch("master")
  )

  private val fork = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://github.com/scala-steward/base.g8-1.git",
    Branch("master")
  )

  private val defaultBranch = BranchOut(
    Branch("master"),
    CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  test("createForkOrGetRepo") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = false).unsafeRunSync()
    assertEquals(repoOut, fork)
  }

  test("createForkOrGetRepo without forking") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = true).unsafeRunSync()
    assertEquals(repoOut, parent)
  }

  test("createForkOrGetRepoWithDefaultBranch") {
    val (repoOut, branchOut) =
      gitHubApiAlg.createForkOrGetRepoWithDefaultBranch(repo, doNotFork = false).unsafeRunSync()
    assertEquals(repoOut, fork)
    assertEquals(branchOut, defaultBranch)
  }

  test("createForkOrGetRepoWithDefaultBranch without forking") {
    val (repoOut, branchOut) =
      gitHubApiAlg.createForkOrGetRepoWithDefaultBranch(repo, doNotFork = true).unsafeRunSync()
    assertEquals(repoOut, parent)
    assertEquals(branchOut, defaultBranch)
  }

  test("closePullRequest") {
    val prOut = gitHubApiAlg.closePullRequest(repo, PullRequestNumber(1347)).unsafeRunSync()
    assertEquals(prOut.state, PullRequestState.Closed)
  }

  test("commentPullRequest") {
    val reference = gitHubApiAlg.referencePullRequest(PullRequestNumber(1347))
    assertEquals(reference, "#1347")
  }

  test("commentPullRequest") {
    val comment = gitHubApiAlg
      .commentPullRequest(repo, PullRequestNumber(1347), "Superseded by #1234")
      .unsafeRunSync()
    assertEquals(comment, Comment("Superseded by #1234"))
  }
}
