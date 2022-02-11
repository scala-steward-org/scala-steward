package org.scalasteward.core.vcs.github

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class GitHubLabels(labels: List[String])

object GitHubLabels {
  implicit val gitHubLabelsEncoder: Encoder[GitHubLabels] = deriveEncoder
}
