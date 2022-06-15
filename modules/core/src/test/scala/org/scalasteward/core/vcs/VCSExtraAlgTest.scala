package org.scalasteward.core.vcs

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.data.ReleaseRelatedUrl
import org.scalasteward.core.mock.MockConfig
import org.scalasteward.core.util._

class VCSExtraAlgTest extends FunSuite {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0" => Ok("exist")
      case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0" => PermanentRedirect()
      case _                                                            => NotFound()
    }

  implicit val client: UrlCheckerClient[IO] =
    UrlCheckerClient[IO](Client.fromHttpApp[IO](routes.orNotFound))
  implicit val urlChecker: UrlChecker[IO] =
    UrlChecker.create[IO](MockConfig.config).unsafeRunSync()

  private val updateFoo = ("com.example".g % "foo".a % "0.1.0" %> "0.2.0").single
  private val updateBar = ("com.example".g % "bar".a % "0.1.0" %> "0.2.0").single
  private val updateBuz = ("com.example".g % "buz".a % "0.1.0" %> "0.2.0").single

  test("getBranchCompareUrl: std vsc") {
    val vcsExtraAlg = VCSExtraAlg.create[IO](MockConfig.config.vcsCfg)

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
    val config = VCSCfg(
      VCSType.GitHub,
      uri"https://github.on-prem.com/",
      "",
      doNotFork = false,
      addLabels = false
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
