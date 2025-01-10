package org.scalasteward.core.coursier

import cats.Id
import cats.syntax.all.*
import munit.FunSuite
import org.http4s.syntax.literals.*

class DependencyMetadataTest extends FunSuite {
  test("filterUrls") {
    val metadata = DependencyMetadata(
      homePage = Some(uri"https://github.com/japgolly/scalajs-react"),
      scmUrl = Some(uri"github.com:japgolly/scalajs-react.git"),
      releaseNotesUrl = None,
      versionScheme = Some("early-semver")
    )
    val obtained = metadata.filterUrls(_.renderString.startsWith("http").pure[Id])
    assertEquals(obtained, metadata.copy(scmUrl = None))
  }

  test("repoUrl: scmUrl with non-http scheme") {
    val homePage = Some(uri"https://github.com/japgolly/scalajs-react")
    val metadata = DependencyMetadata(
      homePage = homePage,
      scmUrl = Some(uri"github.com:japgolly/scalajs-react.git"),
      releaseNotesUrl = None
    )
    assertEquals(metadata.repoUrl, homePage)
  }
}
