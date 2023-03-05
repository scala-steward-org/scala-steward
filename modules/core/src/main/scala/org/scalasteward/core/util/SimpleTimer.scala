/*
 * Copyright 2018-2023 Scala Steward contributors
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

import cats.data.OptionT
import cats.effect.{Async, Ref}
import cats.syntax.all._
import scala.concurrent.duration.FiniteDuration

final class SimpleTimer[F[_]](
    currentDuration: Ref[F, Option[FiniteDuration]],
    lastStart: Ref[F, Option[Timestamp]]
)(implicit dateTimeAlg: DateTimeAlg[F], F: Async[F]) {
  def start(duration: FiniteDuration): F[Unit] =
    currentDuration.set(duration.some) >>
      dateTimeAlg.currentTimestamp.map(Some.apply).flatMap(lastStart.set)

  def remaining: F[Option[FiniteDuration]] =
    OptionT(currentDuration.get).flatMap { duration =>
      OptionT(lastStart.get).flatMapF { setTimestamp =>
        dateTimeAlg.currentTimestamp.flatMap { now =>
          val elapsed = setTimestamp.until(now)
          if (elapsed < duration) F.pure((duration - elapsed).some)
          else currentDuration.set(None).as(none[FiniteDuration])
        }
      }
    }.value

  def await: F[Unit] =
    remaining.flatMap(_.fold(F.unit)(F.sleep))

  def expired: F[Boolean] =
    remaining.map(_.isEmpty)
}

object SimpleTimer {
  def create[F[_]](implicit dateTimeAlg: DateTimeAlg[F], F: Async[F]): F[SimpleTimer[F]] =
    for {
      currentDuration <- Ref[F].of(Option.empty[FiniteDuration])
      lastStart <- Ref[F].of(Option.empty[Timestamp])
    } yield new SimpleTimer(currentDuration, lastStart)
}
