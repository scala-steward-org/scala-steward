package org.scalasteward.core.vcs

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.{Config, SupportedVCS}
import org.scalasteward.core.data.{ReleaseRelatedUrl, Update}
import org.scalasteward.core.mock.MockContext
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

  implicit val cfg = MockContext.config
  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpExistenceClient =
    HttpExistenceClient.create[IO].allocated.map(_._1).unsafeRunSync()

  val updateFoo = Update.Single("com.example" % "foo" % "0.1.0", Nel.of("0.2.0"))
  val updateBar = Update.Single("com.example" % "bar" % "0.1.0", Nel.of("0.2.0"))
  val updateBuz = Update.Single("com.example" % "buz" % "0.1.0", Nel.of("0.2.0"))

  test("getBranchCompareUrl: std vsc") {
    val vcsExtraAlg = VCSExtraAlg.create[IO]

    vcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.com/foo/foo",
        updateFoo
      )
      .unsafeRunSync() shouldBe List.empty

    vcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.com/foo/bar",
        updateBar
      )
      .unsafeRunSync() shouldBe List(
      ReleaseRelatedUrl.VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
    )

    vcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.com/foo/buz",
        updateBuz
      )
      .unsafeRunSync() shouldBe List.empty
  }

  test("getBranchCompareUrl: github on prem") {
    implicit val cfg: Config = MockContext.config.copy(
      vcsType = SupportedVCS.GitHub,
      vcsApiHost = uri"https://github.on-prem.com/"
    )
    val githubOnPremVcsExtraAlg = VCSExtraAlg.create[IO]

    githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.on-prem.com/foo/foo",
        updateFoo
      )
      .unsafeRunSync() shouldBe List.empty

    githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.on-prem.com/foo/bar",
        updateBar
      )
      .unsafeRunSync() shouldBe List(
      ReleaseRelatedUrl.VersionDiff(uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0")
    )

    githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(
        uri"https://github.on-prem.com/foo/buz",
        updateFoo
      )
      .unsafeRunSync() shouldBe List.empty
  }
}
