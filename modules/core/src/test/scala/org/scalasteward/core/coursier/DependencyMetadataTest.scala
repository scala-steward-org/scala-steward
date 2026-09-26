/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
