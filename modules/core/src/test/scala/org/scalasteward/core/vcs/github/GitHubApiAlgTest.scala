package org.scalasteward.core.vcs.github

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.literal._
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
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
            "default_branch": "master",
            "archived": false
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "branches" / "master" =>
        Ok(
          json""" {
            "name": "master",
            "commit": { "sha": "07eb2a203e297c8340273950e98b2cab68b560c1" }
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "branches" / "custom" =>
        Ok(
          json""" {
            "name": "custom",
            "commit": { "sha": "12ea4559063c74184861afece9eeff5ca9d33db3" }
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
              "default_branch": "master",
              "archived": false
            },
            "clone_url": "https://github.com/scala-steward/base.g8-1.git",
            "default_branch": "master",
            "archived": false
          } """
        )

      case POST -> Root / "repos" / "fthomas" / "base.g8" / "issues" / IntVar(_) / "comments" =>
        Created(json"""  {
            "body": "Superseded by #1234"
          } """)

      case POST -> Root / "repos" / "fthomas" / "base.g8" / "issues" / IntVar(_) / "labels" =>
        // Response taken from https://docs.github.com/en/rest/reference/issues#labels, is ignored
        Created(json"""[
          {
            "id": 208045946,
            "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
            "url": "https://api.github.com/repos/fthomas/base.g8/labels/bug",
            "name": "bug",
            "description": "Something isn't working",
            "color": "f29513",
            "default": true
          }]""")

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  val gitHubApiAlg = new GitHubApiAlg[IO](config.vcsCfg.apiHost, _ => IO.pure)

  private val repo = Repo("fthomas", "base.g8")

  private val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/base.g8.git",
    Branch("master")
  )

  private val parentWithCustomDefaultBranch = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/base.g8.git",
    Branch("custom")
  )

  private val fork = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://github.com/scala-steward/base.g8-1.git",
    Branch("master")
  )

  private val forkWithCustomDefaultBranch = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parentWithCustomDefaultBranch),
    uri"https://github.com/scala-steward/base.g8-1.git",
    Branch("custom")
  )

  private val defaultBranch = BranchOut(
    Branch("master"),
    CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  private val defaultCustomBranch = BranchOut(
    Branch("custom"),
    CommitOut(Sha1(HexString("12ea4559063c74184861afece9eeff5ca9d33db3")))
  )

  test("createForkOrGetRepo") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = false).unsafeRunSync()
    assertEquals(repoOut, fork)
  }

  test("createForkOrGetRepo without forking") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = true).unsafeRunSync()
    assertEquals(repoOut, parent)
  }

  test("createForkOrGetRepoWithBranch") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(repo, doNotFork = false)
        .unsafeRunSync()
    assertEquals(repoOut, fork)
    assertEquals(branchOut, defaultBranch)
  }

  test("createForkOrGetRepoWithBranch") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(
          repo.copy(branch = Some(Branch("custom"))),
          doNotFork = false
        )
        .unsafeRunSync()
    assertEquals(repoOut, forkWithCustomDefaultBranch)
    assertEquals(branchOut, defaultCustomBranch)
  }

  test("createForkOrGetRepoWithBranch without forking") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(repo, doNotFork = true)
        .unsafeRunSync()
    assertEquals(repoOut, parent)
    assertEquals(branchOut, defaultBranch)
  }

  test("createForkOrGetRepoWithBranch without forking with custom default branch") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(
          repo.copy(branch = Some(Branch("custom"))),
          doNotFork = true
        )
        .unsafeRunSync()
    assertEquals(repoOut, parentWithCustomDefaultBranch)
    assertEquals(branchOut, defaultCustomBranch)
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

  test("labelPullRequest") {
    val result = gitHubApiAlg
      .labelPullRequest(repo, PullRequestNumber(1347), List("A", "B"))
      .attempt
      .unsafeRunSync()
    assert(result.isRight)
  }
}
