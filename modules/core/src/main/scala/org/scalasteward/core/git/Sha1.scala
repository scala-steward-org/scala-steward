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

package org.scalasteward.core.git

import cats.Eq
import cats.syntax.all.*
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.boolean.{And, Or}
import eu.timepit.refined.char.Digit
import eu.timepit.refined.collection.{Forall, Size}
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.numeric.Interval
import io.circe.refined.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.git.Sha1.HexString

final case class Sha1(value: HexString)

object Sha1 {
  type HexDigit = Digit Or Interval.Closed['a', 'f']
  type HexString = String Refined (Forall[HexDigit] And Size[Equal[40]])
  object HexString extends RefinedTypeOps[HexString, String]

  def from(s: String): Either[Throwable, Sha1] =
    HexString.from(s).bimap(new Throwable(_), Sha1.apply)

  def unsafeFrom(s: String): Sha1 =
    from(s).fold(throw _, identity)

  implicit val sha1Eq: Eq[Sha1] =
    Eq.by(_.value.value)

  implicit val sha1Decoder: Decoder[Sha1] =
    Decoder[HexString].map(Sha1.apply)

  implicit val sha1Encoder: Encoder[Sha1] =
    Encoder[HexString].contramap(_.value)
}
