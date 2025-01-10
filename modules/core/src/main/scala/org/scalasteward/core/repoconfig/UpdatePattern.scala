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
import org.scalasteward.core.data.{GroupId, Update, Version}

final case class UpdatePattern(
    groupId: GroupId,
    artifactId: Option[String],
    version: Option[VersionPattern]
) {
  def isWholeGroupIdAllowed: Boolean = artifactId.isEmpty && version.isEmpty
}

object UpdatePattern {
  final case class MatchResult(
      byArtifactId: List[UpdatePattern],
      filteredVersions: List[Version]
  )

  def findMatch(
      patterns: List[UpdatePattern],
      update: Update.ForArtifactId,
      include: Boolean
  ): MatchResult = {
    val byGroupId = patterns.filter(_.groupId === update.groupId)
    val byArtifactId = byGroupId.filter(_.artifactId.forall(_ === update.artifactId.name))
    val filteredVersions = update.newerVersions.filter(newVersion =>
      byArtifactId.exists(_.version.forall(_.matches(newVersion.value))) === include
    )
    MatchResult(byArtifactId, filteredVersions)
  }

  implicit val updatePatternCodec: Codec[UpdatePattern] =
    deriveCodec
}
