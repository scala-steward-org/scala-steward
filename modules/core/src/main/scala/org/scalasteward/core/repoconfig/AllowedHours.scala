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

import cats.Eq
import cats.syntax.all.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.util.{Nel, Timestamp}

/** The UTC hours of the day when pull requests may be created: hours or half-open ranges of hours,
  * separated by commas. The end of a range is excluded, and a range may cross midnight: "18-7"
  * means from 18:00 until 06:59.
  */
final case class AllowedHours(ranges: Nel[AllowedHours.Range]) {
  def matches(now: Timestamp): Boolean = {
    val hour = now.toLocalDateTime.getHour
    ranges.exists(_.matches(hour))
  }

  def render: String = ranges.map(_.render).toList.mkString(",")
}

object AllowedHours {
  final case class Range(from: Int, to: Int) {
    def matches(hour: Int): Boolean =
      if (from < to) hour >= from && hour < to
      else hour >= from || hour < to

    def render: String = if (to === from + 1) from.toString else s"$from-$to"
  }

  def fromString(s: String): Either[String, AllowedHours] = {
    def hour(part: String, max: Int): Either[String, Int] =
      part.trim.toIntOption.filter(h => 0 <= h && h <= max).toRight(s"'$part' is not an hour")

    def range(part: String): Either[String, Range] =
      part.split("-", -1) match {
        case Array(h)        => hour(h, 23).map(h => Range(h, h + 1))
        case Array(from, to) =>
          (hour(from, 23), hour(to, 24))
            .mapN(Range.apply)
            .filterOrElse(r => r.from =!= r.to, s"'$part' is an empty range")
        case _ => Left(s"'$part' is not an hour or a range of hours")
      }

    Nel
      .fromList(s.split(",", -1).toList)
      .toRight("no hours")
      .flatMap(_.traverse(range))
      .bimap(e => s"invalid hours '$s': $e", AllowedHours.apply)
  }

  implicit val allowedHoursEq: Eq[AllowedHours] =
    Eq.fromUniversalEquals

  implicit val allowedHoursDecoder: Decoder[AllowedHours] =
    Decoder[String].emap(fromString)

  implicit val allowedHoursEncoder: Encoder[AllowedHours] =
    Encoder[String].contramap(_.render)
}
