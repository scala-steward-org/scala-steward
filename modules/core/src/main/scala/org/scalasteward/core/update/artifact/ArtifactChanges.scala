/*
 * Copyright 2018-2022 Scala Steward contributors
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

final case class ArtifactChanges(changes: List[ArtifactChange])

object ArtifactChanges {
  implicit val configuration: Configuration =
    Configuration.default.withDefaults

  implicit val artifactChangesDecoder: Decoder[ArtifactChanges] =
    semiauto.deriveConfiguredDecoder
}
