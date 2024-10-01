package org.scalasteward.core.forge.bitbucketserver

import cats.syntax.semigroupk._
import munit.CatsEffectSuite
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{BasicCredentials, HttpApp, Uri}
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.BitbucketServerCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.{ForgeSelection, ForgeType}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}

class BitbucketServerApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  object FilterTextMatcher extends QueryParamDecoderMatcher[String]("filterText")

  private val repo = Repo("scala-steward-org", "scala-steward")
  private val main = Branch("main")
  private val user = AuthenticatedUser("user", "pass")
  private val userM = MockEff.pure(user)

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))
  private val auth = HttpApp[MockEff] { request =>
    (request: @unchecked) match {
      case _ if !request.headers.get[Authorization].contains(basicAuth) => Forbidden()
    }
  }
  private val httpApp = HttpApp[MockEff] {
    case GET -> Root / "rest" / "default-reviewers" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "conditions" =>
      Ok(s"""[
            |  {
            |    "reviewers": [
            |      {"name": "joe"}
            |    ]
            |  }
            |]""".stripMargin)

    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo =>
      Ok(s"""{
            |  "slug": "${repo.repo}",
            |  "links": { "clone": [ { "href": "http://example.org/scala-steward.git", "name": "http" } ] }
            |}""".stripMargin)

    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "branches" / "default" =>
      Ok(s"""{
            |  "displayId": "main",
            |  "latestCommit": "00213685b18016c86961a7f015793aa09e722db2"
            |}""".stripMargin)

    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "branches" :?
        FilterTextMatcher("main") =>
      Ok(s"""{
            |    "values": [
            |        {
            |            "displayId": "main",
            |            "latestCommit": "8d51122def5632836d1cb1026e879069e10a1e13"
            |        }
            |    ]
            |}""".stripMargin)

    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" =>
      Ok("""{ "values": [
           |  {
           |    "id": 123,
           |    "version": 1,
           |    "title": "Update sbt to 1.4.2",
           |    "state": "open",
           |    "links": { "self": [ { "href": "http://example.org" } ] }
           |  }
           |]}""".stripMargin)

    case POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" =>
      Created(s"""{
                 |    "id": 2,
                 |    "version": 1,
                 |    "title": "scala-steward-pr-title",
                 |    "state": "OPEN",
                 |    "links": {
                 |        "self": [
                 |            {
                 |                "href": "https://example.org/fthomas/base.g8/pullrequests/2"
                 |            }
                 |        ]
                 |    }
                 |}""".stripMargin)

    case POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(1347) / "comments" =>
      Created("""{ "text": "Superseded by #1234" }""")

    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(4711) =>
      Ok("""{
           |  "id": 4711,
           |  "version": 1,
           |  "title": "Update sbt to 1.4.6",
           |  "state": "open",
           |  "links": { "self": [ { "href": "http://example.org" } ] }
           |}""".stripMargin)

    case POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(4711) / "decline" =>
      Ok()

    case _ => NotFound()
  }
  private val state = MockState.empty.copy(clientResponses = auth <+> httpApp)

  private val forgeCfg = config.forgeCfg.copy(tpe = ForgeType.BitbucketServer)
  private val bitbucketServerApiAlg = ForgeSelection
    .forgeApiAlg[MockEff](forgeCfg, BitbucketServerCfg(useDefaultReviewers = false), userM)

  test("createPullRequest") {
    val data = NewPullRequestData(
      title = "scala-steward-pr-title",
      body = "body",
      head = "main",
      base = main,
      labels = Nil,
      assignees = Nil,
      reviewers = Nil
    )
    val pr = bitbucketServerApiAlg.createPullRequest(repo, data).runA(state)
    val expected =
      PullRequestOut(
        html_url = uri"https://example.org/fthomas/base.g8/pullrequests/2",
        state = PullRequestState.Open,
        number = PullRequestNumber(2),
        title = "scala-steward-pr-title"
      )
    assertIO(pr, expected)
  }

  test("createPullRequest - default reviewers") {
    val data = NewPullRequestData(
      title = "scala-steward-pr-title",
      body = "body",
      head = "main",
      base = main,
      labels = Nil,
      assignees = Nil,
      reviewers = Nil
    )
    val apiAlg = ForgeSelection
      .forgeApiAlg[MockEff](forgeCfg, BitbucketServerCfg(useDefaultReviewers = true), userM)
    val pr = apiAlg.createPullRequest(repo, data).runA(state)
    val expected =
      PullRequestOut(
        html_url = uri"https://example.org/fthomas/base.g8/pullrequests/2",
        state = PullRequestState.Open,
        number = PullRequestNumber(2),
        title = "scala-steward-pr-title"
      )
    assertIO(pr, expected)
  }

  test("listPullRequests") {
    val pullRequests = bitbucketServerApiAlg
      .listPullRequests(repo, "update/sbt-1.4.2", main)
      .runA(state)
    val expected = List(
      PullRequestOut(
        Uri.unsafeFromString("http://example.org"),
        PullRequestState.Open,
        PullRequestNumber(123),
        "Update sbt to 1.4.2"
      )
    )

    assertIO(pullRequests, expected)
  }

  test("closePullRequest") {
    val obtained =
      bitbucketServerApiAlg.closePullRequest(repo, PullRequestNumber(4711)).runA(state)
    val expected = PullRequestOut(
      Uri.unsafeFromString("http://example.org"),
      PullRequestState.Closed,
      PullRequestNumber(4711),
      "Update sbt to 1.4.6"
    )
    assertIO(obtained, expected)
  }

  test("commentPullRequest") {
    val comment = bitbucketServerApiAlg
      .commentPullRequest(repo, PullRequestNumber(1347), "Superseded by #1234")
      .runA(state)
    assertIO(comment, Comment("Superseded by #1234"))
  }

  test("getRepo") {
    val obtained = bitbucketServerApiAlg.getRepo(repo).runA(state)
    val expected = RepoOut(
      "scala-steward",
      UserOut("scala-steward-org"),
      None,
      Uri.unsafeFromString("http://example.org/scala-steward.git"),
      Branch("main")
    )
    assertIO(obtained, expected)
  }

  test("getBranch") {
    val obtained = bitbucketServerApiAlg.getBranch(repo, main).runA(state)
    val expected = BranchOut(
      main,
      CommitOut(Sha1.unsafeFrom("8d51122def5632836d1cb1026e879069e10a1e13"))
    )
    assertIO(obtained, expected)
  }
}
