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

package org.scalasteward.core.model

import cats.Order
import cats.implicits._
import org.scalasteward.core.util
import scala.util.Try

final case class Version(value: String) {
  def numericComponents: List[Long] =
    value
      .split(Array('.', '-'))
      .flatMap(util.string.splitNumericAndNonNumeric)
      .map {
        case "SNAP" | "SNAPSHOT" => -3L
        case "M"                 => -2L
        case "RC"                => -1L
        case "RELEASE"           => 0L
        case s                   => Try(s.toLong).getOrElse(0L)
      }
      .toList
}

object Version {
  implicit val versionOrder: Order[Version] =
    Order.from[Version] { (v1, v2) =>
      val c1 = v1.numericComponents
      val c2 = v2.numericComponents
      val maxLength = math.max(c1.length, c2.length)
      val padded1 = c1.padTo(maxLength, 0L)
      val padded2 = c2.padTo(maxLength, 0L)
      padded1.compare(padded2)
    }
}
