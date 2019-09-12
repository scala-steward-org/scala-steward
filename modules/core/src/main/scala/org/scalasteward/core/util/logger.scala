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
import cats.{Foldable, Functor, Monad}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.Update
import scala.concurrent.duration.FiniteDuration
import org.scalasteward.core.implicits._

object logger {
  implicit final class LoggerOps[F[_]](private val logger: Logger[F]) extends AnyVal {
    def attemptLog[A](message: String)(fa: F[A])(
        implicit F: MonadThrowable[F]
    ): F[Either[Throwable, A]] = {
      implicit val l = logger
      logger.info(message) >> fa.attempt.logExceptionWithMessage(s"$message failed")
    }

    def attemptLog_[A](message: String)(fa: F[A])(implicit F: MonadThrowable[F]): F[Unit] =
      attemptLog(message)(fa).void

    def infoTimed[A](msg: FiniteDuration => String)(fa: F[A])(
        implicit
        dateTimeAlg: DateTimeAlg[F],
        F: Monad[F]
    ): F[A] =
      dateTimeAlg.timed(fa).flatMap {
        case (a, duration) => logger.info(msg(duration)) >> F.pure(a)
      }

    def infoTotalTime[A](label: String)(fa: F[A])(
        implicit
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

trait LogAlgSyntax {
  implicit final def syntaxLogEither[F[_], T <: Throwable, A](
      fea: F[Either[T, A]]
  ): LogEitherOps[F, T, A] = new LogEitherOps[F, T, A](fea)
}

final class LogEitherOps[F[_], T <: Throwable, A](private val fea: F[Either[T, A]]) extends AnyVal {

  def logExceptionWithMessage(message: => String)(
      implicit logger: Logger[F],
      F: MonadThrowable[F]
  ): F[Either[T, A]] =
    fea.flatTap {
      case Left(t)  => logger.error(t)(message)
      case Right(_) => F.unit
    }
}
