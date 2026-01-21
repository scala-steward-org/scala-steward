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

import cats.syntax.all.*
import io.circe.Codec
import io.circe.generic.semiauto.*
import org.scalasteward.core.coursier.VersionsCache.VersionWithFirstSeen
import org.scalasteward.core.data.{ArtifactUpdateVersions, GroupId}

final case class UpdatePattern(
    groupId: GroupId,
    artifactId: Option[String] = None,
    version: Option[VersionPattern] = None
) {
  def isWholeGroupIdAllowed: Boolean = artifactId.isEmpty && version.isEmpty
}

object UpdatePattern {
  final case class MatchResult[V](
      byArtifactId: List[UpdatePattern],
      filteredVersions: List[V]
  ) {
    //val hadMatchingVersions: Boolean = byArtifactId.nonEmpty && filteredVersions.nonEmpty
  }


/*
  updates.cooldown = [
  { minimumAge: "1 day", [{groupId = "com.gu"}] },
  { minimumAge: "1 month", [{groupId = "org.scala", artifactId= "scala-lang", version = "3.9"}] },
  { minimumAge: "7 days", [{groupId = "*"}] },
  ]
*/

  /**
   * We feel this is doing two things:
   * 1. Checking whether candidate updates match patterns (mostly for config rules)
   * 2. Filtering to include/exclude some candidate versions
   *
   *
   * MatchResult
   * - No patterns matched ArtifactUpdateVersions
   * - filteredVersions matched against at least one pattern that successfully matched on the group & artifact requirements
   */
  def findMatch[V](
      patterns: List[UpdatePattern],
      update: ArtifactUpdateVersions[V],
      includeMatchingVersions: Boolean
  ): MatchResult[V] = {
    val artifactForUpdate = update.artifactForUpdate
    val byGroupId = patterns.filter(_.groupId === artifactForUpdate.groupId)
    val patternsMatchingByGroupAndArtifactId =
      byGroupId.filter(_.artifactId.forall(_ === artifactForUpdate.artifactId.name))

    val filteredVersions = update.refersToUpdateVersions.filter(newVersion =>
      patternsMatchingByGroupAndArtifactId.exists(
        _.version.forall(_.matches(newVersion.value))
      ) === includeMatchingVersions
    )
    MatchResult(patternsMatchingByGroupAndArtifactId, filteredVersions)
  }

  implicit val updatePatternCodec: Codec[UpdatePattern] =
    deriveCodec
}
