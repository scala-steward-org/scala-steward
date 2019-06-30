/*
 * Copyright 2018-2019 Scala Steward contributors
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

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import scala.concurrent.duration.FiniteDuration

final class LogAlg[F[_]](
    implicit
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def attemptLog_[A](message: String)(fa: F[A]): F[Unit] =
    logger.info(message) >> fa.attempt.flatMap {
      case Left(t)  => logger.error(t)(s"$message failed")
      case Right(_) => F.unit
    }

  def infoTimed[A](msg: FiniteDuration => String)(fa: F[A]): F[A] =
    dateTimeAlg.timed(fa).flatMap {
      case (a, duration) => logger.info(msg(duration)) >> F.pure(a)
    }

  def infoTotalTime[A](label: String)(fa: F[A]): F[A] = {
    val label1 = if (label.nonEmpty) s" $label:" else ""
    infoTimed { duration =>
      string.lineLeftRight(s"Total time:$label1 ${dateTime.showDuration(duration)}")
    }(fa)
  }
}
