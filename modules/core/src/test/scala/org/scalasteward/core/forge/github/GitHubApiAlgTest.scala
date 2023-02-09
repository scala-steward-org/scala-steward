package org.scalasteward.core.forge.github

import cats.syntax.semigroupk._
import io.circe.literal._
import munit.CatsEffectSuite
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{BasicCredentials, HttpApp}
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.GitHubCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.{ForgeSelection, ForgeType}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}

class GitHubApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {

  private val user = AuthenticatedUser("user", "pass")

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))
  private val auth = HttpApp[MockEff] { request =>
    (request: @unchecked) match {
      case _ if !request.headers.get[Authorization].contains(basicAuth) => Forbidden()
    }
  }
  private val httpApp = HttpApp[MockEff] {
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

    case POST -> Root / "repos" / "fthomas" / "base.g8" / "pulls" =>
      Created(json"""  {
            "html_url": "https://github.com/octocat/Hello-World/pull/1347",
            "state": "open",
            "number": 1347,
            "title": "new-feature"
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

    case _ => NotFound()
  }
  private val state = MockState.empty.copy(clientResponses = auth <+> httpApp)

  private val forgeCfg = config.forgeCfg.copy(tpe = ForgeType.GitHub)
  private val gitHubApiAlg = ForgeSelection.forgeApiAlg[MockEff](forgeCfg, GitHubCfg(), user)

  private val repo = Repo("fthomas", "base.g8")

  private val pullRequest =
    PullRequestOut(
      uri"https://github.com/octocat/Hello-World/pull/1347",
      PullRequestState.Open,
      PullRequestNumber(1347),
      "new-feature"
    )

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
    CommitOut(Sha1.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1"))
  )

  private val defaultCustomBranch = BranchOut(
    Branch("custom"),
    CommitOut(Sha1.unsafeFrom("12ea4559063c74184861afece9eeff5ca9d33db3"))
  )

  test("createForkOrGetRepo") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = false).runA(state)
    assertIO(repoOut, fork)
  }

  test("createForkOrGetRepo without forking") {
    val repoOut = gitHubApiAlg.createForkOrGetRepo(repo, doNotFork = true).runA(state)
    assertIO(repoOut, parent)
  }

  test("createForkOrGetRepoWithBranch") {
    gitHubApiAlg
      .createForkOrGetRepoWithBranch(repo, doNotFork = false)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, fork)
        assertEquals(branchOut, defaultBranch)
      }
  }

  test("createForkOrGetRepoWithBranch") {
    gitHubApiAlg
      .createForkOrGetRepoWithBranch(
        repo.copy(branch = Some(Branch("custom"))),
        doNotFork = false
      )
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, forkWithCustomDefaultBranch)
        assertEquals(branchOut, defaultCustomBranch)
      }
  }

  test("createForkOrGetRepoWithBranch without forking") {
    gitHubApiAlg
      .createForkOrGetRepoWithBranch(repo, doNotFork = true)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, parent)
        assertEquals(branchOut, defaultBranch)
      }
  }

  test("createForkOrGetRepoWithBranch without forking with custom default branch") {
    gitHubApiAlg
      .createForkOrGetRepoWithBranch(
        repo.copy(branch = Some(Branch("custom"))),
        doNotFork = true
      )
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, parentWithCustomDefaultBranch)
        assertEquals(branchOut, defaultCustomBranch)
      }
  }

  test("createPullRequest") {
    val data = NewPullRequestData(
      "new-feature",
      "body",
      "aaa",
      Branch("master"),
      Nil
    )
    val pr = gitHubApiAlg.createPullRequest(repo, data).runA(state)
    assertIO(pr, pullRequest)
  }

  test("listPullRequests") {
    val prs = gitHubApiAlg.listPullRequests(repo, "master", Branch("master")).runA(state)
    assertIO(prs, List(pullRequest))
  }

  test("closePullRequest") {
    val prOut = gitHubApiAlg.closePullRequest(repo, PullRequestNumber(1347)).runA(state)
    assertIO(prOut.map(_.state), PullRequestState.Closed)
  }

  test("commentPullRequest") {
    val reference = gitHubApiAlg.referencePullRequest(PullRequestNumber(1347))
    assertEquals(reference, "#1347")
  }

  test("commentPullRequest") {
    val comment = gitHubApiAlg
      .commentPullRequest(repo, PullRequestNumber(1347), "Superseded by #1234")
      .runA(state)
    assertIO(comment, Comment("Superseded by #1234"))
  }

  test("labelPullRequest") {
    gitHubApiAlg
      .labelPullRequest(repo, PullRequestNumber(1347), List("A", "B"))
      .runA(state)
      .assert
  }
}
