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

package org.scalasteward.core.util

import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.concurrent.duration._

object dateTime {
  def showDuration(d: FiniteDuration): String = {
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
    splitDuration(d).map(d1 => d1.length.toString + symbol(d1.unit)).mkString(" ")
  }

  def splitDuration(d: FiniteDuration): List[FiniteDuration] = {
    @tailrec
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
