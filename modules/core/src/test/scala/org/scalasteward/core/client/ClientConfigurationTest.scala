package org.scalasteward.core.client

import org.http4s.client._
import org.http4s.headers.`User-Agent`
import cats.effect._
import cats.implicits._
import org.http4s.implicits._
import org.typelevel.ci._
import munit.CatsEffectSuite
import org.http4s.HttpRoutes
import org.http4s.headers.Location

class ClientConfigurationTest extends CatsEffectSuite {
  private val userAgentValue = "my-user-agent"
  private val dummyUserAgent =
    `User-Agent`.parse(userAgentValue).getOrElse(fail("unable to create user agent"))

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
    }
  }

  test("setUserAgent add a specific user agent to requests") {
    import org.http4s.client.dsl.io._
    import org.http4s.Method._

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
    import org.http4s.blaze.server._
    import org.http4s.client.dsl.io._
    import org.http4s.Method._

    val regularClient = ClientConfiguration.build[IO](
      ClientConfiguration.BuilderMiddleware.default,
      ClientConfiguration.setUserAgent(dummyUserAgent)
    )
    val disabledClient = ClientConfiguration.build[IO](
      ClientConfiguration.disableFollowRedirect,
      ClientConfiguration.setUserAgent(dummyUserAgent)
    )
    val getServer =
      BlazeServerBuilder[IO].bindAny("localhost").withHttpApp(routes.orNotFound).resource

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
}
