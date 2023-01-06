/*
 * Copyright 2018-2023 Scala Steward contributors
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
import io.circe.generic.extras.{semiauto, Configuration}
import org.scalasteward.core.data.GroupId

final case class ArtifactChange(
    groupIdBefore: Option[GroupId],
    groupIdAfter: GroupId,
    artifactIdBefore: Option[String],
    artifactIdAfter: String
)

object ArtifactChange {
  implicit val configuration: Configuration =
    Configuration.default.withDefaults

  implicit val decoder: Decoder[ArtifactChange] =
    semiauto
      .deriveConfiguredDecoder[ArtifactChange]
      .ensure(
        change => change.groupIdBefore.isDefined || change.artifactIdBefore.isDefined,
        "At least one of groupIdBefore and/or artifactIdBefore must be set"
      )
}
