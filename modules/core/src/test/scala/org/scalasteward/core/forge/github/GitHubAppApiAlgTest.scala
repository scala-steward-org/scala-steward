package org.scalasteward.core.forge.github

import io.circe.literal._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}
import org.typelevel.ci.CIStringSyntax

class GitHubAppApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {

  object PerPageMatcher extends QueryParamDecoderMatcher[Int]("per_page")

  private def hasAuthHeader(req: Request[MockEff], authorization: Authorization): Boolean =
    req.headers.get[Authorization].contains(authorization)

  private val jwtToken = "jwt-token-abc123"
  private val ghsToken = "ghs_16C7e42F292c6912E7710c838347Ae178B4a"
  private val jwtAuth = Authorization(Credentials.Token(AuthScheme.Bearer, jwtToken))
  private val tokenAuth = Authorization(Credentials.Token(ci"token", ghsToken))

  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case req @ GET -> Root / "app" / "installations" :? PerPageMatcher(100)
        if hasAuthHeader(req, jwtAuth) =>
      Ok(json"""[
          {
            "id": 1
          },
          {
            "id": 2
          }
        ]""")
    case req @ POST -> Root / "app" / "installations" / "1" / "access_tokens"
        if hasAuthHeader(req, jwtAuth) =>
      Ok(json"""{
              "token": ${ghsToken}
            }""")

    case req @ GET -> Root / "installation" / "repositories" :? PerPageMatcher(100)
        if hasAuthHeader(req, tokenAuth) =>
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
    case _ => NotFound()
  })

  private val gitHubAppApiAlg = new GitHubAppApiAlg[MockEff](config.forgeCfg.apiHost)

  test("installations") {
    val installations = gitHubAppApiAlg.installations(jwtToken).runA(state)
    assertIO(installations, List(InstallationOut(1), InstallationOut(2)))
  }

  test("accessToken") {
    val token = gitHubAppApiAlg.accessToken(jwtToken, 1).runA(state)
    assertIO(token, TokenOut(ghsToken))
  }

  test("repositories") {
    val repositories = gitHubAppApiAlg.repositories(ghsToken).runA(state)
    assertIO(
      repositories,
      RepositoriesOut(List(Repository("fthomas/base.g8"), Repository("octocat/Hello-World")))
    )
  }
}
