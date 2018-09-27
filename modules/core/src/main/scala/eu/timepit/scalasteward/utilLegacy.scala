/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward

import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import scala.concurrent.duration._

object utilLegacy {
  def ifTrue[F[_]: Monad](fb: F[Boolean])(f: F[Unit]): F[Unit] =
    fb.ifM(f, Monad[F].unit)

  def longestCommonPrefix(s1: String, s2: String): String = {
    var i = 0
    val min = math.min(s1.length, s2.length)
    while (i < min && s1(i) == s2(i)) i = i + 1
    s1.substring(0, i)
  }

  def longestCommonPrefix(xs: NonEmptyList[String]): String =
    xs.reduceLeft(longestCommonPrefix)

  def longestCommonNonEmptyPrefix(xs: NonEmptyList[String]): Option[NonEmptyString] =
    NonEmptyString.unapply(longestCommonPrefix(xs))

  def removeSuffix(target: String, suffixes: List[String]): String =
    suffixes
      .find(suffix => target.endsWith(suffix))
      .fold(target)(suffix => target.substring(0, target.length - suffix.length))

  def show(d: FiniteDuration): String = {
    def symbol(unit: TimeUnit): String =
      unit match {
        case DAYS         => "d"
        case HOURS        => "h"
        case MINUTES      => "m"
        case SECONDS      => "s"
        case MILLISECONDS => "ms"
        case MICROSECONDS => "µs"
        case NANOSECONDS  => "ns"
      }
    split(d).map(d1 => d1.length.toString + symbol(d1.unit)).mkString(" ")
  }

  def split(d: FiniteDuration): List[FiniteDuration] = {
    def loop(d1: FiniteDuration, acc: List[FiniteDuration]): List[FiniteDuration] =
      nextUnitAndCap(d1.unit) match {
        case Some((nextUnit, cap)) if d1.length > cap =>
          val next = FiniteDuration(d1.length / cap, nextUnit)
          val capped = FiniteDuration(d1.length % cap, d1.unit)
          loop(next, capped :: acc)
        case _ => d1 :: acc
      }

    def nextUnitAndCap(unit: TimeUnit): Option[(TimeUnit, Long)] =
      unit match {
        case DAYS         => None
        case HOURS        => Some((DAYS, 24L))
        case MINUTES      => Some((HOURS, 60L))
        case SECONDS      => Some((MINUTES, 60L))
        case MILLISECONDS => Some((SECONDS, 1000L))
        case MICROSECONDS => Some((MILLISECONDS, 1000L))
        case NANOSECONDS  => Some((MICROSECONDS, 1000L))
      }

    loop(d, List.empty)
  }
}
