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
import cron4s.lib.javatime.*
import cron4s.syntax.cron.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.repoconfig.PullRequestFrequency.*
import org.scalasteward.core.util.Timestamp
import org.scalasteward.core.util.dateTime.parseFiniteDuration
import scala.concurrent.duration.*

sealed trait PullRequestFrequency {
  def render: String

  def onSchedule(now: Timestamp): Boolean =
    this match {
      case CronExpr(expr) => expr.datePart.allOf(now.toLocalDateTime)
      case _              => true
    }

  def waitingTime(lastCreated: Timestamp, now: Timestamp): Option[FiniteDuration] = {
    val nextPossible = this match {
      case Asap           => None
      case Timespan(fd)   => Some(lastCreated + fd)
      case CronExpr(expr) => expr.next(lastCreated.toLocalDateTime).map(Timestamp.fromLocalDateTime)
    }
    nextPossible.map(now.until).filter(_.length > 0)
  }
}

object PullRequestFrequency {
  case object Asap extends PullRequestFrequency { val render = "@asap" }
  final case class Timespan(fd: FiniteDuration) extends PullRequestFrequency {
    val render: String = fd.toString
  }
  final case class CronExpr(expr: cron4s.CronExpr) extends PullRequestFrequency {
    val render: String = expr.toString
  }

  def fromString(s: String): Either[String, PullRequestFrequency] =
    s.trim.toLowerCase match {
      case Asap.render => Right(Asap)
      case "@daily"    => Right(Timespan(1.day))
      case "@weekly"   => Right(Timespan(7.day))
      case "@monthly"  => Right(Timespan(30.day))
      case other       => parseTimespan(other).orElse(parseCronExpr(other))
    }

  private def parseTimespan(s: String): Either[String, Timespan] =
    parseFiniteDuration(s).bimap(_.toString, Timespan.apply)

  private def parseCronExpr(s: String): Either[String, CronExpr] =
    // cron4s requires exactly 6 fields, but we also want to support the more
    // common format with 5 fields. Therefore we're prepending the "seconds"
    // field ourselves if parsing the original string fails.
    cron4s.Cron.parse(s).orElse(cron4s.Cron.parse("0 " + s)).bimap(_.toString, CronExpr.apply)

  implicit val pullRequestFrequencyEq: Eq[PullRequestFrequency] =
    Eq.fromUniversalEquals

  implicit val pullRequestFrequencyDecoder: Decoder[PullRequestFrequency] =
    Decoder[String].emap(fromString)

  implicit val pullRequestFrequencyEncoder: Encoder[PullRequestFrequency] =
    Encoder[String].contramap(_.render)
}
