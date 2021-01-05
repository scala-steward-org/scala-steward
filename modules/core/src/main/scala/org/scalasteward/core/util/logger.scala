/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.syntax.all._
import cats.{Foldable, Functor, Monad, MonadThrow}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.Update
import scala.concurrent.duration.FiniteDuration

object logger {
  implicit final class LoggerOps[F[_]](private val logger: Logger[F]) extends AnyVal {
    def attemptLogLabel[A](label: String, errorLabel: Option[String] = None)(fa: F[A])(implicit
        F: MonadThrow[F]
    ): F[Either[Throwable, A]] =
      logger.info(label) >> attemptLogError(s"${errorLabel.getOrElse(label)} failed")(fa)

    def attemptLogWarn_[A](message: String)(fa: F[A])(implicit F: MonadThrow[F]): F[Unit] =
      attemptLogImpl_(fa, logger.warn(_)(message))

    def attemptLogError[A](message: String)(fa: F[A])(implicit
        F: MonadThrow[F]
    ): F[Either[Throwable, A]] =
      attemptLogImpl(fa, logger.error(_)(message))

    private def attemptLogImpl[A](fa: F[A], log: Throwable => F[Unit])(implicit
        F: MonadThrow[F]
    ): F[Either[Throwable, A]] =
      fa.attempt.flatTap(_.fold(log, _ => F.unit))

    private def attemptLogImpl_[A](fa: F[A], log: Throwable => F[Unit])(implicit
        F: MonadThrow[F]
    ): F[Unit] =
      fa.attempt.flatMap(_.fold(log, _ => F.unit))

    def infoTimed[A](msg: FiniteDuration => String)(fa: F[A])(implicit
        dateTimeAlg: DateTimeAlg[F],
        F: Monad[F]
    ): F[A] =
      dateTimeAlg.timed(fa).flatMap { case (a, duration) => logger.info(msg(duration)).as(a) }

    def infoTotalTime[A](label: String)(fa: F[A])(implicit
        dateTimeAlg: DateTimeAlg[F],
        F: Monad[F]
    ): F[A] = {
      val label1 = if (label.nonEmpty) s" $label:" else ""
      infoTimed { duration =>
        string.lineLeftRight(s"Total time:$label1 ${dateTime.showDuration(duration)}")
      }(fa)
    }
  }

  def showUpdates[F[_]: Foldable: Functor](updates: F[Update]): String = {
    val list = string.indentLines(updates.map(_.show))
    updates.size match {
      case 0 => s"Found 0 updates"
      case 1 => s"Found 1 update:\n$list"
      case n => s"Found $n updates:\n$list"
    }
  }
}
