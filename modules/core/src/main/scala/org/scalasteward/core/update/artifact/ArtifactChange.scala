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

package org.scalasteward.core.update.artifact

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import org.scalasteward.core.data.GroupId

final case class ArtifactChange(
    before: ArtifactBefore,
    groupIdAfter: GroupId,
    artifactIdAfter: String,
    initialVersion: String
)

object ArtifactChange {
  implicit val configuration: Configuration =
    Configuration.default.withDefaults

  implicit val decoder: Decoder[ArtifactChange] =
    cursor =>
      for {
        groupIdBefore <- cursor.downField("groupIdBefore").as[Option[GroupId]]
        artifactIdBefore <- cursor.downField("artifactIdBefore").as[Option[String]]
        groupIdAfter <- cursor.downField("groupIdAfter").as[GroupId]
        artifactIdAfter <- cursor.downField("artifactIdAfter").as[String]
        initialVersion <- cursor.downField("initialVersion").as[String]
      } yield {
        val before = ArtifactBefore(groupIdBefore, artifactIdBefore)
        ArtifactChange(before, groupIdAfter, artifactIdAfter, initialVersion)
      }

  def apply(
      groupIdBefore: Option[GroupId],
      groupIdAfter: GroupId,
      artifactIdBefore: Option[String],
      artifactIdAfter: String,
      initialVersion: String
  ): ArtifactChange = {
    val before = ArtifactBefore(groupIdBefore, artifactIdBefore)
    ArtifactChange(before, groupIdAfter, artifactIdAfter, initialVersion)
  }
}

sealed trait ArtifactBefore
object ArtifactBefore {
  def apply(groupIdBefore: Option[GroupId], artifactIdBefore: Option[String]): ArtifactBefore =
    (groupIdBefore, artifactIdBefore) match {
      case (Some(groupId), Some(artifactId)) => ArtifactBefore.Full(groupId, artifactId)
      case (Some(groupId), None)             => ArtifactBefore.GroupIdOnly(groupId)
      case (None, Some(artifactId))          => ArtifactBefore.ArtifactIdOnly(artifactId)
      case (None, None) =>
        throw new IllegalArgumentException(
          "At-least one of groupIdBefore and/or artifactIdBefore must be set"
        )
    }

  case class Full(groupId: GroupId, artifactId: String) extends ArtifactBefore
  case class GroupIdOnly(groupId: GroupId) extends ArtifactBefore
  case class ArtifactIdOnly(artifactId: String) extends ArtifactBefore
}
