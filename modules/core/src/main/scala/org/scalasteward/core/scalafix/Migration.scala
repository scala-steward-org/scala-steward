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

package org.scalasteward.core.scalafix

import io.circe.Decoder
import io.circe.generic.semiauto._
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.util.Nel

final case class Migration(
    groupId: GroupId,
    artifactIds: Nel[String],
    newVersion: Version,
    rewriteRules: Nel[String],
    doc: Option[String]
)

object Migration {
  implicit val migrationDecoder: Decoder[Migration] =
    deriveDecoder[Migration]
}
