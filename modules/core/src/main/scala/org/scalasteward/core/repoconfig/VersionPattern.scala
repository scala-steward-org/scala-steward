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

package org.scalasteward.core.repoconfig

import cats.Eq
import cats.syntax.all._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

final case class VersionPattern(
    prefix: Option[String] = None,
    suffix: Option[String] = None,
    exact: Option[String] = None,
    contains: Option[String] = None
) {
  def matches(version: String): Boolean =
    prefix.forall(version.startsWith) &&
      suffix.forall(version.endsWith) &&
      exact.forall(_ === version) &&
      contains.forall(version.contains)
}

object VersionPattern {
  implicit val versionPatternEq: Eq[VersionPattern] =
    Eq.fromUniversalEquals

  implicit val versionPatternDecoder: Decoder[VersionPattern] =
    deriveDecoder[VersionPattern].or(Decoder[String].map(s => VersionPattern(prefix = Some(s))))

  implicit val versionPatternEncoder: Encoder[VersionPattern] =
    deriveEncoder
}
