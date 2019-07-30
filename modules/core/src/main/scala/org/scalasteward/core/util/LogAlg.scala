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
import org.scalasteward.core.implicits._

import scala.concurrent.duration.FiniteDuration

final class LogAlg[F[_]](
    implicit
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def attemptLog[A](message: String)(fa: F[A]): F[Either[Throwable, A]] =
    logger.info(message) >> fa.attempt.logError(s"$message failed")

  def attemptLog_[A](message: String)(fa: F[A]): F[Unit] =
    attemptLog(message)(fa).void

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

trait LogAlgSyntax {
  implicit final def syntaxLogEither[F[_], A](fea: F[Either[Throwable, A]]): LogEitherOps[F, A] =
    new LogEitherOps[F, A](fea)
}

final class LogEitherOps[F[_], A](private val fea: F[Either[Throwable, A]]) extends AnyVal {

  def logError(message: => String)(
      implicit logger: Logger[F],
      F: MonadThrowable[F]
  ): F[Either[Throwable, A]] =
    fea.flatTap {
      case Left(t)  => logger.error(t)(message)
      case Right(_) => F.unit
    }
}
