package org.scalasteward.core.mavencentral

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.literal._
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}
import org.scalasteward.core.util.HttpJsonClient

import java.time.Instant


class MavenCentralApiAlgTest extends FunSuite {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      // https://search.maven.org/solrsearch/select?q=g:%22software.amazon.awssdk%22%20AND%20a:%22s3%22%20AND%20v:%222.17.71%22&rows=20&wt=json
      case GET -> Root / "solrsearch" / "select" =>
        Ok(
          json""" {
  "responseHeader": {
    "status": 0,
    "QTime": 1,
    "params": {
      "q": "g:\"software.amazon.awssdk\" AND a:\"s3\" AND v:\"2.17.71\"",
      "core": "",
      "indent": "off",
      "fl": "id,g,a,v,p,ec,timestamp,tags",
      "start": "",
      "sort": "score desc,timestamp desc,g asc,a asc,v desc",
      "rows": "20",
      "wt": "json",
      "version": "2.2"
    }
  },
  "response": {
    "numFound": 1,
    "start": 0,
    "docs": [
      {
        "id": "software.amazon.awssdk:s3:2.17.71",
        "g": "software.amazon.awssdk",
        "a": "s3",
        "v": "2.17.71",
        "p": "jar",
        "timestamp": 1635540791000,
        "ec": [
          "-javadoc.jar",
          "-sources.jar",
          ".jar",
          ".pom"
        ],
        "tags": [
          "classes",
          "module",
          "with",
          "client",
          "communicating",
          "simple",
          "used",
          "amazon",
          "that",
          "service",
          "java",
          "holds",
          "storage"
        ]
      }
    ]
  }
} """
        )

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val httpJsonClient: HttpJsonClient[IO] = {
    implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
    new HttpJsonClient[IO]
  }

  val mavenCentralApiAlg = new MavenCentralApiAlg[IO]() // (_ => IO.pure)

  test("searchForDependency") {
    val dependency = Dependency(GroupId("software.amazon.awssdk"), ArtifactId("s3"), "2.17.71")

    val docOpt = mavenCentralApiAlg.searchForDocumentOn(dependency).unsafeRunSync()
    assertEquals(docOpt.map(_.timestamp),Some(Instant.parse("2021-10-29T20:53:11Z")))
  }
}