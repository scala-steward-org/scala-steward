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

package org.scalasteward.core.repoconfig

import cats.Eq
import cats.implicits._
import cron4s.lib.javatime._
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.repoconfig.PullRequestFrequency._
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration._

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
      case Daily          => Some(lastCreated + 1.day)
      case Weekly         => Some(lastCreated + 7.days)
      case Monthly        => Some(lastCreated + 30.days)
      case CronExpr(expr) => expr.next(lastCreated.toLocalDateTime).map(Timestamp.fromLocalDateTime)
    }
    nextPossible.map(now.until).filter(_.length > 0)
  }
}

object PullRequestFrequency {
  case object Asap extends PullRequestFrequency { val render = "@asap" }
  case object Daily extends PullRequestFrequency { val render = "@daily" }
  case object Weekly extends PullRequestFrequency { val render = "@weekly" }
  case object Monthly extends PullRequestFrequency { val render = "@monthly" }
  final case class CronExpr(expr: cron4s.CronExpr) extends PullRequestFrequency {
    val render: String = expr.toString
  }

  val default: PullRequestFrequency = Asap

  def fromString(s: String): Either[String, PullRequestFrequency] =
    s.trim.toLowerCase match {
      case Asap.render    => Right(Asap)
      case Daily.render   => Right(Daily)
      case Weekly.render  => Right(Weekly)
      case Monthly.render => Right(Monthly)
      case other          =>
        // cron4s requires exactly 6 fields, but we also want to support the more
        // common format with 5 fields. Therefore we're prepending the "seconds"
        // field ourselves if parsing the original string fails.
        parseCron4s(other).orElse(parseCron4s("0 " + other)).leftMap(_.toString).map(CronExpr.apply)
    }

  private def parseCron4s(s: String): Either[Throwable, cron4s.CronExpr] =
    Either.catchNonFatal(cron4s.Cron.unsafeParse(s))

  implicit val pullRequestFrequencyEq: Eq[PullRequestFrequency] =
    Eq.fromUniversalEquals

  implicit val pullRequestFrequencyDecoder: Decoder[PullRequestFrequency] =
    Decoder[String].emap(fromString)

  implicit val pullRequestFrequencyEncoder: Encoder[PullRequestFrequency] =
    Encoder[String].contramap(_.render)
}
