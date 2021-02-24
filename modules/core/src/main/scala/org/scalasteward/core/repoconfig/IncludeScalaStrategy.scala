/*
 * Copyright 2018-2021 Scala Steward contributors
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
import io.circe.{Decoder, Encoder}

sealed trait IncludeScalaStrategy {
  def name: String
}

object IncludeScalaStrategy {
  final case object Yes extends IncludeScalaStrategy { val name = "yes" }
  final case object Draft extends IncludeScalaStrategy { val name = "draft" }
  final case object No extends IncludeScalaStrategy { val name = "no" }

  val default: IncludeScalaStrategy = Draft

  def fromString(value: String): IncludeScalaStrategy =
    value.trim.toLowerCase match {
      case Yes.name   => Yes
      case Draft.name => Draft
      case No.name    => No
      case _          => default
    }

  def fromBoolean(value: Boolean): IncludeScalaStrategy =
    if (value) Yes else No

  implicit val includeScalaStrategyDecoder: Decoder[IncludeScalaStrategy] =
    Decoder[Boolean].map(fromBoolean).or(Decoder[String].map(fromString))

  implicit val includeScalaStrategyEncoder: Encoder[IncludeScalaStrategy] =
    Encoder[String].contramap(_.name)

  implicit val includeScalaStrategyEq: Eq[IncludeScalaStrategy] =
    Eq.fromUniversalEquals
}
