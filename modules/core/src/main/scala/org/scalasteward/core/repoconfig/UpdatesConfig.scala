/*
 * Copyright 2018-2020 Scala Steward contributors
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

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.data.Update
import org.scalasteward.core.update.FilterAlg.{FilterResult, IgnoredByConfig, NotAllowedByConfig}
import org.scalasteward.core.util.Nel

final case class UpdatesConfig(
    allow: List[UpdatePattern] = List.empty,
    ignore: List[UpdatePattern] = List.empty,
    limit: Option[Int] = None
) {
  def keep(update: Update.Single): FilterResult =
    isAllowed(update).flatMap(isIgnored)

  private def isAllowed(update: Update.Single): FilterResult = {
    val m = UpdatePattern.findMatch(allow, update, include = true)
    if (m.filteredVersions.nonEmpty) {
      Right(update.copy(newerVersions = Nel.fromListUnsafe(m.filteredVersions)))
    } else if (m.byArtifactId.isEmpty)
      Right(update)
    else Left(NotAllowedByConfig(update))
  }

  private def isIgnored(update: Update.Single): FilterResult = {
    val m = UpdatePattern.findMatch(ignore, update, include = false)
    if (m.filteredVersions.nonEmpty)
      Right(update.copy(newerVersions = Nel.fromListUnsafe(m.filteredVersions)))
    else
      Left(IgnoredByConfig(update))
  }
}

object UpdatesConfig {
  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  implicit val updatesConfigDecoder: Decoder[UpdatesConfig] =
    deriveConfiguredDecoder

  implicit val updatesConfigEncoder: Encoder[UpdatesConfig] =
    deriveConfiguredEncoder
}
