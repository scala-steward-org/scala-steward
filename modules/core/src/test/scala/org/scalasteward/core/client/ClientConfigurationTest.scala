package org.scalasteward.core.client

import cats.effect._
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{`Retry-After`, `User-Agent`, Location}
import org.http4s.implicits._
import org.typelevel.ci._

import java.time.Instant
import scala.concurrent.duration._
import scala.util.Try

class ClientConfigurationTest extends CatsEffectSuite {

  private val previousEpochSecond = Instant.now().minusSeconds(1).getEpochSecond
  private val nextEpochSecond = Instant.now().plusSeconds(1).getEpochSecond
  private val userAgentValue = "my-user-agent"
  private val dummyUserAgent =
    `User-Agent`.parse(1)(userAgentValue).getOrElse(fail("unable to create user agent"))

  private val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ GET -> Root / "user-agent" =>
        req.headers.get(ci"user-agent") match {
          case Some(value) =>
            if (value.head.value == userAgentValue) Ok("success")
            else
              IO.raiseError(
                new RuntimeException(s"expected $userAgentValue but got ${value.head.value}")
              )
          case None =>
            BadRequest("No user-agent")
        }

      case GET -> Root / "redirect" =>
        Found(Location(uri"/redirected"))

      case GET -> Root / "redirected" =>
        BadRequest("Got redirected")

      case req @ GET -> Root / "retry-after" =>
        val maybeAttempt = req.headers.get(ci"X-Attempt").flatMap(_.head.value.toIntOption)
        maybeAttempt match {
          case Some(attempt) if attempt >= 2 =>
            Ok()
          case _ =>
            val resetHeader =
              ParseResult.success(Header.Raw(ci"X-Ratelimit-Reset", s"$nextEpochSecond"))
            Forbidden().map(_.putHeaders(`Retry-After`.fromLong(1), resetHeader))
        }
      case req @ GET -> Root / "rate-limit-reset" / epochSecondsParam =>
        req.headers.get(ci"X-Attempt").flatMap(_.head.value.toIntOption) match {
          case Some(attempt) if attempt >= 2 => Ok()
          case _ =>
            val seconds = Try(epochSecondsParam.toLong).getOrElse(0L)
            val resetHeader =
              ParseResult.success(Header.Raw(ci"X-Ratelimit-Reset", seconds.toString))
            Forbidden().map(_.putHeaders(resetHeader))
        }
    }

  test("setUserAgent add a specific user agent to requests") {
    val initialClient = Client.fromHttpApp[IO](routes.orNotFound)
    val setUserAgent = ClientConfiguration.setUserAgent[IO](dummyUserAgent)
    val newClient = setUserAgent(initialClient)

    for {
      _ <- newClient
        .run(GET(uri"/user-agent"))
        .use(r => r.status.code.pure[IO])
        .assertEquals(200)
      _ <- initialClient
        .run(GET(uri"/user-agent"))
        .use(r => r.status.code.pure[IO])
        .assertEquals(400)
    } yield ()
  }

  test("disableFollowRedirect does not follow redirect") {
    val regularClient = ClientConfiguration.build[IO](
      ClientConfiguration.BuilderMiddleware.default,
      ClientConfiguration.setUserAgent(dummyUserAgent)
    )
    val disabledClient = ClientConfiguration.build[IO](
      ClientConfiguration.disableFollowRedirect,
      ClientConfiguration.setUserAgent(dummyUserAgent)
    )
    val getServer =
      EmberServerBuilder
        .default[IO]
        .withShutdownTimeout(1.second)
        .withHttpApp(routes.orNotFound)
        .build

    val test = (regularClient, disabledClient, getServer).tupled.use {
      case (regClient, disClient, s) =>
        val fullUri = s.baseUri / "redirect"
        (
          regClient.run(GET(fullUri)).use(_.status.code.pure[IO]),
          disClient.run(GET(fullUri)).use(_.status.code.pure[IO])
        ).tupled
    }
    test.assertEquals((400, 302))
  }

  test("retries on retry-after response header even though 'X-Ratelimit-Reset' exists") {
    val notEnoughRetries = request(uri"/retry-after", 1).assertEquals(403)
    val exactlyEnoughRetries = request(uri"/retry-after", 2).assertEquals(200)
    notEnoughRetries.flatMap(_ => exactlyEnoughRetries)
  }

  test("retries with the value mentioned in 'X-Ratelimit-Reset' in the absense of 'Retry-After'") {
    val uri = Uri.unsafeFromString(s"/rate-limit-reset/$nextEpochSecond")
    val notEnoughRetries = request(uri, 1).assertEquals(403)
    val exactlyEnoughRetries = request(uri, 2).assertEquals(200)
    notEnoughRetries.flatMap(_ => exactlyEnoughRetries)
  }

  test("retries after 1 second when the given value in 'X-Ratelimit-Reset' elapsed") {
    val uri: Uri = Uri.unsafeFromString(s"/rate-limit-reset/$previousEpochSecond")
    val notEnoughRetries = request(uri, 1).assertEquals(403)
    val exactlyEnoughRetries = request(uri, 2).assertEquals(200)
    notEnoughRetries.flatMap(_ => exactlyEnoughRetries)
  }

  private def request(uri: Uri, attempts: PosInt): IO[Int] =
    clientWithMaxAttempts(attempts).run(GET(uri)).use(r => r.status.code.pure[IO])

  private def clientWithMaxAttempts(maxAttempts: PosInt): Client[IO] = {
    val initialClient = Client.fromHttpApp[IO](routes.orNotFound)
    val withRetry = ClientConfiguration.withRetry[IO](maxAttempts)
    withRetry(initialClient)
  }
}
