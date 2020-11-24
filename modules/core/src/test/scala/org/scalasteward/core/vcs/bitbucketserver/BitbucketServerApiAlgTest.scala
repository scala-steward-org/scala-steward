package org.scalasteward.core.vcs.bitbucketserver

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BitbucketServerApiAlgTest extends AnyFunSuite with Matchers {
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "rest" / "api" / "1.0" / "projects" / repo.owner / "repos" / repo.repo / "pull-requests" =>
      Ok("""{ "values": [
           |  {
           |    "id": 123,
           |    "title": "Update sbt to 1.4.2",
           |    "state": "open",
           |    "links": { "self": [ { "href": "http://example.org" } ] }
           |  }
           |]}""".stripMargin)
  }

  implicit private val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit private val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  private val bitbucketServerApiAlg: BitbucketServerApiAlg[IO] =
    new BitbucketServerApiAlg[IO](config.vcsApiHost, _ => IO.pure, useReviewers = false)

  test("listPullRequests") {
    val pullRequests = bitbucketServerApiAlg
      .listPullRequests(repo, "update/sbt-1.4.2", Branch("main"))
      .unsafeRunSync()

    pullRequests shouldBe List(
      PullRequestOut(
        Uri.unsafeFromString("http://example.org"),
        PullRequestState.Open,
        PullRequestNumber(123),
        "Update sbt to 1.4.2"
      )
    )
  }
}
