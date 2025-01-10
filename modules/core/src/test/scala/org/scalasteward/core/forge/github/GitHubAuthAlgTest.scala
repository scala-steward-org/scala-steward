package org.scalasteward.core.forge.github

import better.files.File
import io.circe.literal.*
import munit.CatsEffectSuite
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.syntax.literals.*
import org.http4s.{AuthScheme, Credentials, HttpApp, Request}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.{httpJsonClient, logger}
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}
import org.typelevel.ci.CIStringSyntax
import scala.concurrent.duration.*

class GitHubAuthAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  object PerPageMatcher extends QueryParamDecoderMatcher[Int]("per_page")

  private def hasAuthHeader(req: Request[MockEff], authorization: Authorization): Boolean =
    req.headers.get[Authorization].contains(authorization)

  private val pemFile = File(getClass.getResource("/rsa-4096-private.pem"))
  private val gitHubAuthAlg = new GitHubAuthAlg[MockEff](uri"http://localhost", 42L, pemFile)
  private val nowMillis = 1673743729714L
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
                "token": $ghsToken
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

  test("createJWT with ttl") {
    val obtained = gitHubAuthAlg.createJWT(2.minutes, nowMillis).runA(state)
    val expected =
      "eyJhbGciOiJSUzI1NiJ9.eyJpYXQiOjE2NzM3NDM3MjksImlzcyI6IjQyIiwiZXhwIjoxNjczNzQzODQ5fQ.SDW4TqjokzYAwHD6joDdgqCtQyPrq-4QThanWB12vNUkjNtP4gw9iiG_baWBNXi4nlA6_HtO0H_WNKO6God6vkHz_ERBbIUb7I2vhp17NEb8vRECUksqARnrAzPU8HPUZPD5V7uehEDxEa-Tv-eI3L8iH8JVWx-m60vAZdBi76IQ094mIXf_d1TC75HKpap1wPMV7i_973IVAuL6zu2Sy6bkhHAS0WAQKStSAolFvwih7uq2f6N1b-1ogopFtkL6w19lQ4iRSvaoXPvkyBuvw6DqowVcAWon8-OB9cdzUIsjQs5GkR4IwCQQOBp-9_NYKBRDyVTwa-vqBBlYcOc_Zzd-_tpK3zRLpsh-h8_p0W8YAQrYAVyJRWn128Mm72jc2q9DkWhsiIGGWr44p3z6DENypgx3HiFDZbcvgMhPJKeNY3CwYh2QK56XtPNcbYSmUzog1IkX5lrM3WOO9j1bfj8tTP5h46dYXApvTq2-q5zlLP66Rm40RQnc_TE_6ntVq1kKn6IQ0yqEuPN0GVwoX71PElnajufz_Bzn08-YtYMK2Ca-t-wKWapDaH9zDjWUoXe_Pbcb5T_AZkbqPy8MHkzRzkMFSACwrXjHDuq_PphdlHZJeIb4xJ0PSp4f6urz_TRdxFmrTlG-e7DaKcoOLMbp8VK419TD3VinXq3MGDs"
    assertIO(obtained, expected)
  }

  test("createJWT without ttl") {
    val obtained = gitHubAuthAlg.createJWT(0.minutes, nowMillis).runA(state)
    val expected =
      "eyJhbGciOiJSUzI1NiJ9.eyJpYXQiOjE2NzM3NDM3MjksImlzcyI6IjQyIn0.GcJ2RzzwgN-decPz0BNhNwrMFh6Wjj2xtbH0bOWEBGolnclEymJDT0QrjojvVw7iDabq5FezOGgPYP6JXlykMQlXFjX7TFeBAsydpZt1wyU1N8PQwxpoUtumksBGgTqNuIWg6_Y8CQg-UTbM4B63axcNREz6iT43a0cKxNe0ABy6jwcWSXw2Ck5Ob2uS_ZMCAt3VapIovT7Vci0goI7z6eXF8l6FpJauSgiVRXYsOAoZwXnDeNU1LkWFkGtWh9vK4iyaI_IDc85f3ODU5KfiPHOWuy2h7j6WPKEMXQTLXiiGQr_HqP4ROR-HXW7hlpyBFsrL44EqNe3oQcnTWNdOAj2s2K0aLzMm1XmeenPKgMeJcDvp8q_lRFKC54En4bHKZZEccOVnfItEb7D7fkBuWUYM5-k6cb4CPZyPrOvO5zBsQyboW2_Zcrpr_mGelm9rdSQ29azIvu2G2gBWY_QsT54E1_D3uN4HbsUsTxwjJPXlw2ScFgn_4wGu3XuU9QfIzipw4-PJtXo9deoHMinji0VuXzAZslJMyCoKqvCOV7voVNQOuQJroVeahVY1cU-dWLWOfrOcJ0LZRxZ2gIoRztc1wawfmNix8mFGNXei_qY0M5LZtOgWfdgIsmrUF17s1mX2Lwp2mlvjvCCP6qcXQnrn6GWit_ihcOb2IFR9yIw"
    assertIO(obtained, expected)
  }

  test("installations") {
    val installations = gitHubAuthAlg.installations(jwtToken).runA(state)
    assertIO(installations, List(InstallationOut(1), InstallationOut(2)))
  }

  test("accessToken") {
    val token = gitHubAuthAlg.accessToken(jwtToken, 1).runA(state)
    assertIO(token, TokenOut(ghsToken))
  }

  test("repositories") {
    val repositories = gitHubAuthAlg.repositories(ghsToken).runA(state)
    assertIO(
      repositories,
      List(Repo("fthomas", "base.g8"), Repo("octocat", "Hello-World"))
    )
  }
}
