package org.scalasteward.core.data

import org.http4s.Uri

sealed trait ReleaseRelatedUrl {
  def url: Uri
}

object ReleaseRelatedUrl {
  final case class CustomChangelog(url: Uri) extends ReleaseRelatedUrl
  final case class CustomReleaseNotes(url: Uri) extends ReleaseRelatedUrl
  final case class GitHubReleaseNotes(url: Uri) extends ReleaseRelatedUrl
  final case class VersionDiff(url: Uri) extends ReleaseRelatedUrl
}
