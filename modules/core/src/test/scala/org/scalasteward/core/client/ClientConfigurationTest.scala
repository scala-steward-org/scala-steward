package org.scalasteward.core.client

import cats.effect._
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import munit.CatsEffectSuite
import org.http4s.HttpRoutes
import org.http4s.client._
import org.http4s.headers.{`Retry-After`, `User-Agent`, Location}
import org.http4s.implicits._
import org.typelevel.ci._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration._

class ClientConfigurationTest extends CatsEffectSuite {

  private val userAgentValue = "my-user-agent"
  private val dummyUserAgent =
    `User-Agent`.parse(1)(userAgentValue).getOrElse(fail("unable to create user agent"))

  private val routes: HttpRoutes[IO] = {
    import org.http4s.dsl.io._
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
            Forbidden().map(_.putHeaders(`Retry-After`.fromLong(1)))
        }
    }
  }

  test("setUserAgent add a specific user agent to requests") {
    import org.http4s.Method._
    import org.http4s.client.dsl.io._

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
    import org.http4s.Method._
    import org.http4s.ember.server._
    import org.http4s.client.dsl.io._

    implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

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

  test("retries on retry-after response header") {
    import org.http4s.Method._
    import org.http4s.client.dsl.io._

    def clientWithMaxAttempts(maxAttempts: PosInt): Client[IO] = {
      val initialClient = Client.fromHttpApp[IO](routes.orNotFound)
      val retryAfter = ClientConfiguration.retryAfter[IO](maxAttempts)
      retryAfter(initialClient)
    }

    val notEnoughRetries = clientWithMaxAttempts(1)
      .run(GET(uri"/retry-after"))
      .use(r => r.status.code.pure[IO])
      .assertEquals(403)

    val exactlyEnoughRetries = clientWithMaxAttempts(2)
      .run(GET(uri"/retry-after"))
      .use(r => r.status.code.pure[IO])
      .assertEquals(200)

    notEnoughRetries.flatMap(_ => exactlyEnoughRetries)
  }
}
