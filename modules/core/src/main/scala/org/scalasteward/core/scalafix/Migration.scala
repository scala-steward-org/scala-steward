/*
 * Copyright 2018-2019 Scala Steward contributors
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

import org.scalasteward.core.data.{GroupId, Version}
import cats.Eq
import org.scalasteward.core.util.Nel
import scala.util.matching.Regex
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe.Decoder._
import cats.syntax.eq._
import cats.instances.string._

final case class Migration(
    groupId: GroupId,
    artifactIds: Nel[Regex],
    newVersion: Version,
    rewriteRules: Nel[String]
) {

  override def equals(x: Any): Boolean = {
    implicit val regexEq: Eq[Regex] = Eq.by(_.regex)
    lazy val other: Migration = x.asInstanceOf[Migration]
    x.isInstanceOf[Migration] &&
    other.artifactIds === this.artifactIds &&
    other.groupId === this.groupId &&
    other.newVersion === this.newVersion &&
    other.rewriteRules === this.rewriteRules
  }
}

object Migration {
  implicit val regexDecoder: Decoder[Regex] = decodeString.map(_.r)
  implicit val versionDecoder: Decoder[Version] = decodeString.map(Version.apply)
  implicit val migrationDecoder: Decoder[Migration] = deriveDecoder[Migration]
}
