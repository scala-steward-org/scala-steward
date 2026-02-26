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

import cats.implicits.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import org.scalasteward.core.data.ArtifactUpdateCandidates
import org.scalasteward.core.util.Timestamp
import org.scalasteward.core.util.dateTime.parseFiniteDuration

import scala.concurrent.duration.FiniteDuration

final case class CooldownConfig(
    minimumAge: FiniteDuration
) {
  def filterForAge(
      updateCandidates: ArtifactUpdateCandidates,
      currentTime: Timestamp
  ): Option[ArtifactUpdateCandidates] =
    updateCandidates.filterVersionsWithFirstSeen(_.isOlderThan(minimumAge, currentTime))
}

object CooldownConfig {
  implicit val codec: Codec[CooldownConfig] = deriveCodec
  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].emap(s => parseFiniteDuration(s).leftMap(_.toString))
  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)
}
