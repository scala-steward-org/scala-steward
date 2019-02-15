/*
 * Copyright 2018-2019 scala-steward contributors
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

import cats.implicits._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.scalasteward.core.model.Update

final case class RepoConfig(
    ignoreDependencies: List[String] = List.empty
) {
  def isIgnored(update: Update.Single): Boolean =
    ignoreDependencies.contains_(s"${update.groupId}:${update.artifactId}")
}

object RepoConfig {
  implicit val repoConfigDecoder: Decoder[RepoConfig] =
    deriveDecoder
}
