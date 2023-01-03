package org.scalasteward.core.vcs.bitbucketserver

import munit.CatsEffectSuite
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{BasicCredentials, HttpApp, Request, Uri}
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.BitbucketServerCfg
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.{VCSSelection, VCSType}

class BitbucketServerApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  object FilterTextMatcher extends QueryParamDecoderMatcher[String]("filterText")

  private val repo = Repo("scala-steward-org", "scala-steward")
  private val main = Branch("main")
  private val user = AuthenticatedUser("user", "pass")

  private def hasAuthHeader(req: Request[MockEff], authorization: Authorization): Boolean =
    req.headers.get[Authorization].contains(authorization)

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))

  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case req @ GET -> Root / "rest" / "default-reviewers" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "conditions"
        if hasAuthHeader(req, basicAuth) =>
      Ok(s"""[
            |  {
            |    "reviewers": [
            |      {"name": "joe"}
            |    ]
            |  }
            |]""".stripMargin)

    case req @ GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo
        if hasAuthHeader(req, basicAuth) =>
      Ok(s"""{
            |  "slug": "${repo.repo}",
            |  "links": { "clone": [ { "href": "http://example.org/scala-steward.git", "name": "http" } ] }
            |}""".stripMargin)

    case req @ GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "branches" / "default"
        if hasAuthHeader(req, basicAuth) =>
      Ok(s"""{
            |  "displayId": "main",
            |  "latestCommit": "00213685b18016c86961a7f015793aa09e722db2"
            |}""".stripMargin)

    case req @ GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "branches" :?
        FilterTextMatcher("main") if hasAuthHeader(req, basicAuth) =>
      Ok(s"""{
            |    "values": [
            |        {
            |            "displayId": "main",
            |            "latestCommit": "8d51122def5632836d1cb1026e879069e10a1e13"
            |        }
            |    ]
            |}""".stripMargin)

    case req @ GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests"
        if hasAuthHeader(req, basicAuth) =>
      Ok("""{ "values": [
           |  {
           |    "id": 123,
           |    "version": 1,
           |    "title": "Update sbt to 1.4.2",
           |    "state": "open",
           |    "links": { "self": [ { "href": "http://example.org" } ] }
           |  }
           |]}""".stripMargin)

    case req @ POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests"
        if hasAuthHeader(req, basicAuth) =>
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

    case req @ POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(1347) / "comments" if hasAuthHeader(req, basicAuth) =>
      Created("""{ "text": "Superseded by #1234" }""")

    case req @ GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(4711) if hasAuthHeader(req, basicAuth) =>
      Ok("""{
           |  "id": 4711,
           |  "version": 1,
           |  "title": "Update sbt to 1.4.6",
           |  "state": "open",
           |  "links": { "self": [ { "href": "http://example.org" } ] }
           |}""".stripMargin)

    case req @ POST -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" /
        IntVar(4711) / "decline" if hasAuthHeader(req, basicAuth) =>
      Ok()

    case _ => NotFound()
  })

  private val bitbucketServerApiAlg = new VCSSelection[MockEff](
    config.copy(
      vcsCfg = config.vcsCfg.copy(tpe = VCSType.BitbucketServer),
      bitbucketServerCfg = BitbucketServerCfg(useDefaultReviewers = false)
    ),
    user
  ).vcsApiAlg

  test("createPullRequest") {
    val data = NewPullRequestData(
      title = "scala-steward-pr-title",
      body = "body",
      head = "main",
      base = main,
      labels = Nil
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
      labels = Nil
    )
    val pr = new VCSSelection[MockEff](
      config.copy(
        vcsCfg = config.vcsCfg.copy(tpe = VCSType.BitbucketServer),
        bitbucketServerCfg = BitbucketServerCfg(useDefaultReviewers = false)
      ),
      user
    ).vcsApiAlg.createPullRequest(repo, data).runA(state)
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
      CommitOut(Sha1(HexString.unsafeFrom("8d51122def5632836d1cb1026e879069e10a1e13")))
    )
    assertIO(obtained, expected)
  }
}
