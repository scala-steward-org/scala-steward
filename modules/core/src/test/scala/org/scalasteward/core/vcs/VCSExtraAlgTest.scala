package org.scalasteward.core.vcs

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.util.{HttpExistenceClient, Nel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VCSExtraAlgTest extends AnyFunSuite with Matchers {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0" => Ok("exist")
      case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0" => PermanentRedirect()
      case _                                                            => NotFound()
    }

  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpExistenceClient = new HttpExistenceClient[IO]

  val vcsExtraAlg = VCSExtraAlg.create[IO]
  val updateFoo = Update.Single(GroupId("com.example"), "foo", "0.1.0", Nel.of("0.2.0"))
  val updateBar = Update.Single(GroupId("com.example"), "bar", "0.1.0", Nel.of("0.2.0"))
  val updateBuz = Update.Single(GroupId("com.example"), "buz", "0.1.0", Nel.of("0.2.0"))

  test("getBranchCompareUrl") {
    vcsExtraAlg.getBranchCompareUrl(None, updateBar).unsafeRunSync() shouldBe None
    vcsExtraAlg
      .getBranchCompareUrl(Some("https://github.com/foo/foo"), updateFoo)
      .unsafeRunSync() shouldBe None
    vcsExtraAlg
      .getBranchCompareUrl(Some("https://github.com/foo/bar"), updateBar)
      .unsafeRunSync() shouldBe Some("https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
    vcsExtraAlg
      .getBranchCompareUrl(Some("https://github.com/foo/buz"), updateBuz)
      .unsafeRunSync() shouldBe None
  }
}
