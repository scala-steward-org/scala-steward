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
import io.circe.syntax.*
import io.circe.{Codec, Decoder, Encoder, Json}
import org.scalasteward.core.data.ArtifactUpdateCandidates
import org.scalasteward.core.util.Timestamp
import org.scalasteward.core.util.dateTime.parseFiniteDuration

import scala.concurrent.duration.FiniteDuration

final case class CooldownConfig(
    minimumAge: FiniteDuration,
    overrides: Option[List[CooldownConfig.Override]] = None
) {
  def overridesOrDefault: List[CooldownConfig.Override] = overrides.getOrElse(Nil)

  private def minimumAgeFor(updateCandidates: ArtifactUpdateCandidates): FiniteDuration =
    overridesOrDefault
      .find { o =>
        UpdatePattern
          .findMatch(List(o.pattern), updateCandidates, include = true)
          .byArtifactId
          .nonEmpty
      }
      .map(_.minimumAge)
      .getOrElse(minimumAge)

  def filterForAge(
      updateCandidates: ArtifactUpdateCandidates,
      currentTime: Timestamp
  ): Option[ArtifactUpdateCandidates] = {
    val effectiveAge = minimumAgeFor(updateCandidates)
    updateCandidates.filterVersionsWithFirstSeen(_.isOlderThan(effectiveAge, currentTime))
  }
}

object CooldownConfig {
  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].emap(s => parseFiniteDuration(s).leftMap(_.toString))
  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  final case class Override(
      pattern: UpdatePattern,
      minimumAge: FiniteDuration
  )

  object Override {
    implicit val codec: Codec[Override] = Codec.from(
      Decoder.instance { c =>
        for {
          minimumAge <- c.get[FiniteDuration]("minimumAge")
          pattern <- c.as[UpdatePattern]
        } yield Override(pattern, minimumAge)
      },
      Encoder.instance { o =>
        o.pattern.asJson.deepMerge(Json.obj("minimumAge" -> o.minimumAge.asJson))
      }
    )
  }

  implicit val codec: Codec[CooldownConfig] = deriveCodec
}
