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

package org.scalasteward.core.repoconfig

import cats.Eq
import cats.syntax.all.*
import io.circe.*
import io.circe.syntax.*
import org.scalasteward.core.data.{SemVer, Update}
import scala.util.matching.Regex

final case class PullRequestUpdateFilter private (
    group: Option[String] = None,
    artifact: Option[String] = None,
    version: Option[SemVer.Change] = None
) {

  /** Returns `true` if an update falls into this filter; returns `false` otherwise.
    */
  def matches(update: Update.ForArtifactId): Boolean =
    groupRegex.forall(_.matches(update.groupId.value)) &&
      artifactRegex.forall(_.matches(update.mainArtifactId)) &&
      version.forall(isMatchedVersion(_, update))

  private lazy val groupRegex = group.map(wildcardRegex)
  private lazy val artifactRegex = artifact.map(wildcardRegex)

  private def wildcardRegex(groupOrArtifact: String) = {
    val pattern = Regex.quote(groupOrArtifact).replaceAll("\\*", "\\\\E.+\\\\Q")
    new Regex(pattern)
  }

  private def isMatchedVersion(versionType: SemVer.Change, update: Update.ForArtifactId): Boolean =
    (SemVer.parse(update.currentVersion.value), SemVer.parse(update.nextVersion.value)).tupled
      .flatMap { case (current, next) => SemVer.getChangeEarly(current, next) }
      .map(_.render === versionType.render)
      .getOrElse(false)

}

object PullRequestUpdateFilter {

  def apply(
      group: Option[String] = None,
      artifact: Option[String] = None,
      version: Option[SemVer.Change] = None
  ): Either[String, PullRequestUpdateFilter] =
    if (group.isEmpty && artifact.isEmpty && version.isEmpty)
      Left("At least one predicate should be added to the filter")
    else Right(new PullRequestUpdateFilter(group, artifact, version))

  implicit val pullRequestUpdateDecoder: Decoder[PullRequestUpdateFilter] = { cursor =>
    for {
      group <- cursor.get[Option[String]]("group")
      artifact <- cursor.get[Option[String]]("artifact")
      version <- cursor.get[Option[SemVer.Change]]("version")
      filter <- apply(group, artifact, version).leftMap(DecodingFailure(_, Nil))
    } yield filter
  }

  implicit val pullRequestUpdateFilterEq: Eq[PullRequestUpdateFilter] =
    Eq.fromUniversalEquals

  implicit val pullRequestUpdateFilterEncoder: Encoder[PullRequestUpdateFilter] = filter =>
    Json.obj(
      "group" := filter.group,
      "artifact" := filter.artifact,
      "version" := filter.version
    )

}
