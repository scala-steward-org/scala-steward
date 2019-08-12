package org.scalasteward.core.vcs

import cats.effect.{Async, IO}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io.{->, /, HEAD, NotFound, Ok, Root, _}
import org.http4s.implicits._
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.{HttpExistenceClient, Nel}
import org.scalatest.{FunSuite, Matchers}

class VCSExtraAlgTest extends FunSuite with Matchers {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0" => Ok("exist")
      case _                                                            => NotFound()
    }

  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpExistenceClient = new HttpExistenceClient[IO]
  implicit val mockEffAsync = Async[IO]

  val vcsExtraAlg = VCSExtraAlg.create[IO](httpExistenceClient, mockEffAsync)
  val updateFoo = Update.Single("com.example", "foo", "0.1.0", Nel.of("0.2.0"))
  val updateBar = Update.Single("com.example", "bar", "0.1.0", Nel.of("0.2.0"))

  test("getBranchCompareUrl") {
    vcsExtraAlg.getBranchCompareUrl(None, updateBar).unsafeRunSync() shouldBe None
    vcsExtraAlg
      .getBranchCompareUrl(Some("https://github.com/foo/foo"), updateFoo)
      .unsafeRunSync() shouldBe None
    vcsExtraAlg
      .getBranchCompareUrl(Some("https://github.com/foo/bar"), updateBar)
      .unsafeRunSync() shouldBe Some("https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
  }
}
