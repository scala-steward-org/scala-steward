/*
 * Copyright 2018-2021 Scala Steward contributors
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
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor}
import org.scalasteward.core.data.{GroupId, Update}

final case class UpdatePattern(
    groupId: GroupId,
    artifactId: Option[String],
    version: Option[UpdatePattern.Version]
) {
  def isWholeGroupIdAllowed: Boolean = artifactId.isEmpty && version.isEmpty
}

object UpdatePattern {
  final case class MatchResult(
      byArtifactId: List[UpdatePattern],
      filteredVersions: List[String]
  )

  final case class Version(prefix: Option[String], suffix: Option[String]) {
    def matches(version: String): Boolean =
      suffix.forall(version.endsWith) && prefix.forall(version.startsWith)
  }

  def findMatch(
      patterns: List[UpdatePattern],
      update: Update.Single,
      include: Boolean
  ): MatchResult = {
    val byGroupId = patterns.filter(_.groupId === update.groupId)
    val byArtifactId = byGroupId.filter(_.artifactId.forall(_ === update.artifactId.name))
    val filteredVersions = update.newerVersions.filter(newVersion =>
      byArtifactId.exists(_.version.forall(_.matches(newVersion))) === include
    )
    MatchResult(byArtifactId, filteredVersions)
  }

  implicit val updatePatternDecoder: Decoder[UpdatePattern] =
    deriveDecoder

  implicit val updatePatternEncoder: Encoder[UpdatePattern] =
    deriveEncoder

  implicit val updatePatternVersionDecoder: Decoder[Version] =
    Decoder[String]
      .map(s => Version(Some(s), None))
      .or((hCursor: HCursor) =>
        for {
          prefix <- hCursor.downField("prefix").as[Option[String]]
          suffix <- hCursor.downField("suffix").as[Option[String]]
        } yield Version(prefix, suffix)
      )

  implicit val eqVersion: Eq[Version] = Eq.fromUniversalEquals

  implicit val updatePatternVersionEncoder: Encoder[Version] =
    deriveEncoder
}
