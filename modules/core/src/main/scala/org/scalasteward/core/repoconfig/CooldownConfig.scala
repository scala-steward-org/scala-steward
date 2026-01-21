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
import org.scalasteward.core.coursier.VersionsCache.VersionWithFirstSeen
import org.scalasteward.core.data.ArtifactUpdateCandidates
import org.scalasteward.core.update.FilterAlg.TooYoungForCooldown
import org.scalasteward.core.util.dateTime.parseFiniteDuration
import org.scalasteward.core.util.{Nel, Timestamp}

import scala.concurrent.duration.FiniteDuration


final case class CooldownConfig(
    minimumAge: FiniteDuration,
    artifacts: Nel[UpdatePattern]
) {

  /**
   * @return [[None]] if the config rule did not apply for this update, otherwise the result of applying the
   *         config - either a filtered update, where only sufficiently mature versions remain, or a
   *         [[TooYoungForCooldown]] [[org.scalasteward.core.update.FilterAlg.RejectionReason]] if there were
   *         no sufficiently mature versions.
   */
  def relevantConfigAppliedTo(update: ArtifactUpdateCandidates, currentTime: Timestamp): Option[Either[TooYoungForCooldown, ArtifactUpdateCandidates]] = {
    val m = UpdatePattern.findMatch[VersionWithFirstSeen](
      artifacts.toList,
      update,
      includeMatchingVersions = true
    )

    Option.when(m.filteredVersions.nonEmpty) {
      val sufficientlyMatureVersions =
        m.filteredVersions.filter(_.firstSeen.exists(_.until(currentTime) > minimumAge))
      val versionsWithNoMaturityRequirements = update.newerVersionsWithFirstSeen.toList.toSet -- m.filteredVersions

      val survivingVersions = versionsWithNoMaturityRequirements ++ sufficientlyMatureVersions
      Nel.fromList(survivingVersions.toList.sortBy(_.version))
        .map(list => update.copy(newerVersionsWithFirstSeen = list))
        .toRight(TooYoungForCooldown(update))
    }
  }

}

object CooldownConfig {
  implicit val codec: Codec[CooldownConfig] = deriveCodec
  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].emap(s => parseFiniteDuration(s).leftMap(_.toString))
  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)
}
