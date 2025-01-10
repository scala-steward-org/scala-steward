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

package org.scalasteward.core.util

import cats.syntax.all.*
import cats.{Monad, MonadThrow}
import org.scalasteward.core.data.Update
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.FiniteDuration

object logger {
  final class AttemptLoggerOps[F[_]](
      logger: Logger[F],
      logThrowable: (Throwable, String) => F[Unit]
  )(implicit F: MonadThrow[F]) {
    def log[A](msg: String)(fa: F[A]): F[Either[Throwable, A]] =
      fa.attempt.flatTap(_.fold(t => logThrowable(t, msg), _ => F.unit))

    def log_[A](msg: String)(fa: F[A]): F[Unit] =
      log(msg)(fa).void

    def label[A](infoLabel: String, errorLabel: Option[String] = None)(
        fa: F[A]
    ): F[Either[Throwable, A]] =
      logger.info(infoLabel) >> log(s"${errorLabel.getOrElse(infoLabel)} failed")(fa)

    def label_[A](infoLabel: String, errorLabel: Option[String] = None)(fa: F[A]): F[Unit] =
      label(infoLabel, errorLabel)(fa).void
  }

  implicit final class LoggerOps[F[_]](private val logger: Logger[F]) extends AnyVal {
    def attemptWarn(implicit F: MonadThrow[F]): AttemptLoggerOps[F] =
      new AttemptLoggerOps(logger, logger.warn(_)(_))

    def attemptError(implicit F: MonadThrow[F]): AttemptLoggerOps[F] =
      new AttemptLoggerOps(logger, logger.error(_)(_))

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

  def showUpdates(allUpdates: List[Update]): String = {
    val updates = allUpdates.map {
      case g: Update.Grouped =>
        g.updates.map(_.show).mkString(s"${g.name} (group) {\n    ", "\n    ", "\n  }")
      case u: Update.Single => u.show
    }

    val list = string.indentLines(updates)
    updates.size match {
      case 0 => s"Found 0 updates"
      case 1 => s"Found 1 update:\n$list"
      case n => s"Found $n updates:\n$list"
    }
  }
}
