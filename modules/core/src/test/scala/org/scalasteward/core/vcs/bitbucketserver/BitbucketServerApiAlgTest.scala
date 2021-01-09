package org.scalasteward.core.vcs.bitbucketserver

import cats.effect.IO
import munit.FunSuite
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Uri}
import org.scalasteward.core.application.Config.BitbucketServerCfg
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._

class BitbucketServerApiAlgTest extends FunSuite {
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
  }

  implicit private val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit private val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  private val bitbucketServerCfg = BitbucketServerCfg(useDefaultReviewers = false)
  private val bitbucketServerApiAlg: BitbucketServerApiAlg[IO] =
    new BitbucketServerApiAlg[IO](config.vcsApiHost, bitbucketServerCfg, _ => IO.pure)

  test("listPullRequests") {
    val pullRequests = bitbucketServerApiAlg
      .listPullRequests(repo, "update/sbt-1.4.2", Branch("main"))
      .unsafeRunSync()
    val expected = List(
      PullRequestOut(
        Uri.unsafeFromString("http://example.org"),
        PullRequestState.Open,
        PullRequestNumber(123),
        "Update sbt to 1.4.2"
      )
    )

    assertEquals(pullRequests, expected)
  }

  test("closePullRequest") {
    val obtained =
      bitbucketServerApiAlg.closePullRequest(repo, PullRequestNumber(4711)).unsafeRunSync()
    val expected = PullRequestOut(
      Uri.unsafeFromString("http://example.org"),
      PullRequestState.Closed,
      PullRequestNumber(4711),
      "Update sbt to 1.4.6"
    )
    assertEquals(obtained, expected)
  }

  test("commentPullRequest") {
    val comment = bitbucketServerApiAlg
      .commentPullRequest(repo, PullRequestNumber(1347), "Superseded by #1234")
      .unsafeRunSync()
    assertEquals(comment, Comment("Superseded by #1234"))
  }

  test("getRepo") {
    val obtained = bitbucketServerApiAlg.getRepo(repo).unsafeRunSync()
    val expected = RepoOut(
      "scala-steward",
      UserOut("scala-steward-org"),
      None,
      Uri.unsafeFromString("http://example.org/scala-steward.git"),
      Branch("main")
    )
    assertEquals(obtained, expected)
  }
}
