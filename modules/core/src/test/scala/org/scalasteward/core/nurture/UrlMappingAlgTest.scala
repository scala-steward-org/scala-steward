package org.scalasteward.core.nurture

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io.{->, /, :?, GET, Ok, QueryParamDecoderMatcher, Root, _}
import org.http4s.implicits._
import org.scalasteward.core.TestInstances
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.scaladex.{HttpScaladexClient, ScaladexAlg}
import org.scalatest.{FunSuite, Matchers}

class UrlMappingAlgTest extends FunSuite with Matchers {

  object QueryParam extends QueryParamDecoderMatcher[String]("q")
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "search" :? QueryParam("sbt-scalatra") =>
      Ok("<h4>scalatra/sbt-scalatra</h4>")

    case GET -> Root / owner / repo / artifact =>
      Ok(s""" <a href="https://github.com/${owner}/${repo}">GitHub<a>
            | <p>${artifact}</p>
            |""".stripMargin)
  }

  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpScaladexClient = new HttpScaladexClient[IO]
  implicit val coursierAlg = CoursierAlg.create[IO]
  implicit val scaladexAlg = ScaladexAlg.create[IO]
  implicit val logger = TestInstances.ioLogger
  val urlMappingAlg = UrlMappingAlg.create[IO]

  test("getArtifactUrl") {
    val dependencies = List(
      Dependency("org.typelevel", "cats-core", "cats-core_2.12", "1.6.0"),
      Dependency("org.scalatra.sbt", "sbt-scalatra", "sbt-scalatra_2.12", "1.0.1")
    )
    urlMappingAlg.getArtifactIdUrlMapping(dependencies).unsafeRunSync() shouldBe Map(
      "cats-core" -> "https://github.com/typelevel/cats",
      "sbt-scalatra" -> "https://github.com/scalatra/sbt-scalatra"
    )
  }
}
