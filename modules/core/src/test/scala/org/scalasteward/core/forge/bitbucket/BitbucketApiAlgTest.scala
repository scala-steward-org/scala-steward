package org.scalasteward.core.forge.bitbucket

import cats.syntax.semigroupk._
import io.circe.literal._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.BitbucketCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.{ForgeSelection, ForgeType}
import org.scalasteward.core.git._
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}

class BitbucketApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {

  private val user = AuthenticatedUser("user", "pass")

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))
  private val auth = HttpApp[MockEff] { request =>
    (request: @unchecked) match {
      case _ if !request.headers.get[Authorization].contains(basicAuth) => Forbidden()
    }
  }
  private val httpApp = HttpApp[MockEff] {
    case GET -> Root / "repositories" / "fthomas" / "base.g8" =>
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
    case GET -> Root / "repositories" / "null-parenthood" / "base.g8" =>
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
          },
          "parent": null
        }"""
      )
    case GET -> Root / "repositories" / "scala-steward" / "base.g8" =>
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
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "master" =>
      Ok(
        json"""{
          "name": "master",
          "target": {
              "hash": "07eb2a203e297c8340273950e98b2cab68b560c1"
          }
        }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "custom" =>
      Ok(
        json"""{
          "name": "custom",
          "target": {
              "hash": "12ea4559063c74184861afece9eeff5ca9d33db3"
          }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "forks" =>
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
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
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
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "default-reviewers" =>
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
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
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
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" / IntVar(
          _
        ) / "decline" =>
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
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" /
        IntVar(_) / "comments" =>
      Created(json"""{
                  "content": {
                      "raw": "Superseded by #1234"
                  }
          }""")
    case _ => NotFound()
  }
  private val state = MockState.empty.copy(clientResponses = auth <+> httpApp)

  private val forgeCfg = config.forgeCfg.copy(tpe = ForgeType.Bitbucket)
  private val bitbucketCfg = BitbucketCfg(useDefaultReviewers = true)
  private val bitbucketApiAlg = ForgeSelection.forgeApiAlg[MockEff](forgeCfg, bitbucketCfg, user)

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
    CommitOut(Sha1.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1"))
  )

  private val defaultCustomBranch = BranchOut(
    custom,
    CommitOut(Sha1.unsafeFrom("12ea4559063c74184861afece9eeff5ca9d33db3"))
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

  test("createForkOrGetRepo without forking - handle null parent") {
    val repoOut = bitbucketApiAlg
      .createForkOrGetRepo(Repo("null-parenthood", "base.g8"), doNotFork = true)
      .runA(state)
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
      title = "scala-steward-pr",
      body = "body",
      head = "master",
      base = master,
      labels = Nil,
      assignees = Nil,
      reviewers = Nil
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
