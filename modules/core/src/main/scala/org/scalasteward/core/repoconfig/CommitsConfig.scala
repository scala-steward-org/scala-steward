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

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

final case class CommitsConfig(
    message: Option[String] = None
) {
  def messageOrDefault: String =
    message.getOrElse(CommitsConfig.defaultMessage)
}

object CommitsConfig {
  val defaultMessage = "${default}"

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  implicit val commitsConfigCodec: Codec[CommitsConfig] =
    deriveConfiguredCodec
}
