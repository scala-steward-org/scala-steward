package org.scalasteward.core.vcs.github

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.literal._
import munit.FunSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.ci.CIStringSyntax

class GitHubAppApiAlgTest extends FunSuite {

  object PerPageMatcher extends QueryParamDecoderMatcher[Int]("per_page")

  private def hasAuthHeader(req: Request[IO], value: String): Boolean =
    req.headers.headers.contains(Header.Raw(ci"Authorization", value))

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "app" / "installations" :? PerPageMatcher(100)
        if hasAuthHeader(req, "Bearer jwt-token") =>
      Ok(json"""[
          {
            "id": 1
          },
          {
            "id": 2
          }
        ]""")
    case req @ POST -> Root / "app" / "installations" / "1" / "access_tokens"
        if hasAuthHeader(req, "Bearer jwt-token") =>
      Ok(json"""{
              "token": "ghs_16C7e42F292c6912E7710c838347Ae178B4a"
            }""")

    case req @ GET -> Root / "installation" / "repositories" :? PerPageMatcher(100)
        if hasAuthHeader(req, "token ghs_16C7e42F292c6912E7710c838347Ae178B4a") =>
      Ok(json"""{
          "repositories": [
            {
              "full_name": "fthomas/base.g8"
            },
            {
              "full_name": "octocat/Hello-World"
            }
         ]
        }""")
  }

  implicit private val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit private val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  private val gitHubAppApiAlg = new GitHubAppApiAlg[IO](config.vcsCfg.apiHost)

  test("installations") {
    val installations = gitHubAppApiAlg.installations("jwt-token").unsafeRunSync()
    assertEquals(installations, List(InstallationOut(1), InstallationOut(2)))
  }

  test("accessToken") {
    val token = gitHubAppApiAlg.accessToken("jwt-token", 1).unsafeRunSync()
    assertEquals(token, TokenOut("ghs_16C7e42F292c6912E7710c838347Ae178B4a"))
  }

  test("repositories") {
    val repositories =
      gitHubAppApiAlg.repositories("ghs_16C7e42F292c6912E7710c838347Ae178B4a").unsafeRunSync()
    assertEquals(
      repositories,
      RepositoriesOut(List(Repository("fthomas/base.g8"), Repository("octocat/Hello-World")))
    )
  }
}
