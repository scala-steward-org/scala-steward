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

package org.scalasteward.core.nurture

import cats.Order
import org.http4s.Uri

/** A URL of a resource that provides additional information for an update. */
sealed trait UpdateInfoUrl {
  def url: Uri
}

object UpdateInfoUrl {
  final case class CustomChangelog(url: Uri) extends UpdateInfoUrl
  final case class CustomReleaseNotes(url: Uri) extends UpdateInfoUrl
  final case class GitHubReleaseNotes(url: Uri) extends UpdateInfoUrl
  final case class VersionDiff(url: Uri) extends UpdateInfoUrl

  implicit val updateInfoUrlOrder: Order[UpdateInfoUrl] =
    Order.by {
      case GitHubReleaseNotes(url) => (0, url)
      case CustomReleaseNotes(url) => (1, url)
      case CustomChangelog(url)    => (2, url)
      case VersionDiff(url)        => (3, url)
    }
}
