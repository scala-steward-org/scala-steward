package org.scalasteward.core.vcs

import cats.effect.IO
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.SupportedVCS
import org.scalasteward.core.data.{ReleaseRelatedUrl, Update}
import org.scalasteward.core.mock.MockContext
import org.scalasteward.core.util.{Nel, UrlChecker}

class VCSExtraAlgTest extends FunSuite {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0" => Ok("exist")
      case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0" => PermanentRedirect()
      case _                                                            => NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val urlChecker: UrlChecker[IO] =
    UrlChecker.create[IO](MockContext.config).allocated.map(_._1).unsafeRunSync()

  private val updateFoo = Update.Single("com.example" % "foo" % "0.1.0", Nel.of("0.2.0"))
  private val updateBar = Update.Single("com.example" % "bar" % "0.1.0", Nel.of("0.2.0"))
  private val updateBuz = Update.Single("com.example" % "buz" % "0.1.0", Nel.of("0.2.0"))

  test("getBranchCompareUrl: std vsc") {
    val vcsExtraAlg = VCSExtraAlg.create[IO](MockContext.config)

    assertEquals(
      vcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.com/foo/foo", updateFoo)
        .unsafeRunSync(),
      List.empty
    )

    assertEquals(
      vcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.com/foo/bar", updateBar)
        .unsafeRunSync(),
      List(
        ReleaseRelatedUrl.VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
      )
    )

    assertEquals(
      vcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.com/foo/buz", updateBuz)
        .unsafeRunSync(),
      List.empty
    )
  }

  test("getBranchCompareUrl: github on prem") {
    val config = MockContext.config.copy(
      vcsType = SupportedVCS.GitHub,
      vcsApiHost = uri"https://github.on-prem.com/"
    )
    val githubOnPremVcsExtraAlg = VCSExtraAlg.create[IO](config)

    assertEquals(
      githubOnPremVcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/foo", updateFoo)
        .unsafeRunSync(),
      List.empty
    )

    assertEquals(
      githubOnPremVcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/bar", updateBar)
        .unsafeRunSync(),
      List(
        ReleaseRelatedUrl.VersionDiff(
          uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0"
        )
      )
    )

    assertEquals(
      githubOnPremVcsExtraAlg
        .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/buz", updateFoo)
        .unsafeRunSync(),
      List.empty
    )
  }
}
