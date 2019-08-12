package org.scalasteward.core.util

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.dsl.io.{->, /, _}
import org.http4s.implicits._
import org.http4s.{Http4sLiteralSyntax, HttpRoutes}
import org.scalatest.{FunSuite, Matchers}

class UrlExistenceTest extends FunSuite with Matchers {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case HEAD -> Root / "foo" / "foo" / "releases" / "tag" / "v0.1.0" =>
        Ok("exist")

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpExistenceClient: HttpExistenceClient[IO] = new HttpExistenceClient[IO]

  test("should exist") {
    httpExistenceClient
      .exists(uri"https://github.com/foo/foo/releases/tag/v0.1.0")
      .unsafeRunSync() shouldBe true

    httpExistenceClient
      .exists("https://github.com/foo/foo/releases/tag/v0.1.0")
      .unsafeRunSync() shouldBe true
  }

  test("should not exist") {
    httpExistenceClient
      .exists(uri"https://github.com/foo/foo/releases/tag/v0.2.0")
      .unsafeRunSync() shouldBe false

    httpExistenceClient
      .exists("https://example.com")
      .unsafeRunSync() shouldBe false
  }

}
