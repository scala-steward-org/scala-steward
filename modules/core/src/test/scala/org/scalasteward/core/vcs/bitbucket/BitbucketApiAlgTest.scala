package org.scalasteward.core.vcs.bitbucket

import io.circe.literal._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.BitbucketCfg
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git._
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.{VCSSelection, VCSType}

class BitbucketApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {

  private def hasAuthHeader(req: Request[MockEff], authorization: Authorization): Boolean =
    req.headers.get[Authorization].contains(authorization)

  private val user = AuthenticatedUser("user", "pass")
  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))

  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case req @ GET -> Root / "repositories" / "fthomas" / "base.g8"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "fthomas"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/fthomas/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case req @ GET -> Root / "repositories" / "scala-steward" / "base.g8"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "scala-steward"
          },
          "parent": {
              "full_name": "fthomas/base.g8"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case req @ GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "master"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "name": "master",
          "target": {
              "hash": "07eb2a203e297c8340273950e98b2cab68b560c1"
          }
        }"""
      )
    case req @ GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "custom"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "name": "custom",
          "target": {
              "hash": "12ea4559063c74184861afece9eeff5ca9d33db3"
          }
        }"""
      )
    case req @ POST -> Root / "repositories" / "fthomas" / "base.g8" / "forks"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
            "type": "branch",
            "name": "master"
          },
          "owner": {
            "nickname": "scala-steward"
          },
          "parent": {
            "full_name": "fthomas/base.g8"
          },
          "links": {
            "clone": [
                {
                    "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                    "name": "https"
                }
            ]
          }
        }"""
      )
    case req @ POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
            "id": 2,
            "title": "scala-steward-pr",
            "state": "OPEN",
            "links": {
                "html": {
                    "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                }
            }
          }"""
      )
    case req @ GET -> Root / "repositories" / "fthomas" / "base.g8" / "default-reviewers"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "values": [
            {
                "uuid": "{874fbd19-d98a-4f32-860c-476b9288b1b1}"
            },
            {
                "uuid": "{af922393-90eb-4d59-a4f8-222f7bd45495}"
            }
          ]
        }"""
      )
    case req @ GET -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests"
        if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
          "values": [
              {
                  "id": 2,
                  "title": "scala-steward-pr",
                  "state": "OPEN",
                  "links": {
                      "html": {
                          "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                      }
                  }
              }
          ]
      }"""
      )
    case req @ POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" / IntVar(
          _
        ) / "decline" if hasAuthHeader(req, basicAuth) =>
      Ok(
        json"""{
            "id": 2,
            "title": "scala-steward-pr",
            "state": "DECLINED",
            "links": {
                "html": {
                    "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                }
            }
        }"""
      )
    case req @ POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" /
        IntVar(_) / "comments" if hasAuthHeader(req, basicAuth) =>
      Created(json"""{
                  "content": {
                      "raw": "Superseded by #1234"
                  }
          }""")
    case _ => NotFound()
  })

  private val bitbucketApiAlg = new VCSSelection[MockEff](
    config.copy(
      vcsCfg = config.vcsCfg.copy(tpe = VCSType.Bitbucket),
      bitbucketCfg = BitbucketCfg(useDefaultReviewers = true)
    ),
    user
  ).vcsApiAlg

  private val prUrl = uri"https://bitbucket.org/fthomas/base.g8/pullrequests/2"
  private val repo = Repo("fthomas", "base.g8")
  private val master = Branch("master")
  private val custom = Branch("custom")
  private val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://scala-steward@bitbucket.org/fthomas/base.g8.git",
    master
  )

  private val parentWithCustomDefaultBranch = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://scala-steward@bitbucket.org/fthomas/base.g8.git",
    custom
  )

  private val fork = RepoOut(
    "base.g8",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
    master
  )

  private val forkWithCustomDefaultBranch = RepoOut(
    "base.g8",
    UserOut("scala-steward"),
    Some(parentWithCustomDefaultBranch),
    uri"https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
    custom
  )

  private val defaultBranch = BranchOut(
    master,
    CommitOut(Sha1(HexString.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  private val defaultCustomBranch = BranchOut(
    custom,
    CommitOut(Sha1(HexString.unsafeFrom("12ea4559063c74184861afece9eeff5ca9d33db3")))
  )

  private val pullRequest =
    PullRequestOut(prUrl, PullRequestState.Open, PullRequestNumber(2), "scala-steward-pr")

  test("createForkOrGetRepo") {
    val repoOut = bitbucketApiAlg.createForkOrGetRepo(repo, doNotFork = false).runA(state)
    assertIO(repoOut, fork)
  }

  test("createForkOrGetRepo without forking") {
    val repoOut = bitbucketApiAlg.createForkOrGetRepo(repo, doNotFork = true).runA(state)
    assertIO(repoOut, parent)
  }

  test("createForkOrGetRepoWithBranch") {
    bitbucketApiAlg
      .createForkOrGetRepoWithBranch(repo, doNotFork = false)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, fork)
        assertEquals(branchOut, defaultBranch)
      }
  }

  test("createForkOrGetRepoWithBranch with custom default branch") {
    bitbucketApiAlg
      .createForkOrGetRepoWithBranch(repo.copy(branch = Some(custom)), doNotFork = false)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, forkWithCustomDefaultBranch)
        assertEquals(branchOut, defaultCustomBranch)
      }
  }

  test("createForkOrGetRepoWithBranch without forking") {
    bitbucketApiAlg
      .createForkOrGetRepoWithBranch(repo, doNotFork = true)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, parent)
        assertEquals(branchOut, defaultBranch)
      }
  }

  test("createForkOrGetRepoWithBranch without forking with custom default branch") {
    bitbucketApiAlg
      .createForkOrGetRepoWithBranch(repo.copy(branch = Some(custom)), doNotFork = true)
      .runA(state)
      .map { case (repoOut, branchOut) =>
        assertEquals(repoOut, parentWithCustomDefaultBranch)
        assertEquals(branchOut, defaultCustomBranch)
      }
  }

  test("createPullRequest") {
    val data = NewPullRequestData(
      "scala-steward-pr",
      "body",
      "master",
      master,
      Nil
    )
    val pr = bitbucketApiAlg.createPullRequest(repo, data).runA(state)
    assertIO(pr, pullRequest)
  }

  test("listPullRequests") {
    val prs = bitbucketApiAlg.listPullRequests(repo, "master", master).runA(state)
    assertIO(prs, List(pullRequest))
  }

  test("closePullRequest") {
    bitbucketApiAlg
      .closePullRequest(repo, PullRequestNumber(1))
      .runA(state)
      .map(pr => assertEquals(pr, pr.copy(state = PullRequestState.Closed)))
  }

  test("commentPullRequest") {
    val comment = bitbucketApiAlg
      .commentPullRequest(repo, PullRequestNumber(1), "Superseded by #1234")
      .runA(state)
    assertIO(comment, Comment("Superseded by #1234"))
  }
}
