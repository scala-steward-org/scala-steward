/*
 * Copyright 2018-2019 scala-steward contributors
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

import cats.effect.Sync
import cats.implicits._
import cats.{Foldable, Functor}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.model.Update
import scala.concurrent.duration.FiniteDuration

object logger {
  implicit final class LoggerOps[F[_]](val self: Logger[F]) {
    def attemptLog_[A](message: String)(fa: F[A])(
        implicit F: MonadThrowable[F]
    ): F[Unit] =
      self.info(message) >> fa.attempt.flatMap {
        case Left(t)  => self.error(t)(s"$message failed")
        case Right(_) => F.unit
      }

    def infoTimed[A](msg: FiniteDuration => String)(fa: F[A])(implicit F: Sync[F]): F[A] =
      dateTime.timed(fa).flatMap {
        case (a, duration) => self.info(msg(duration)) >> F.pure(a)
      }

    def infoTotalTime[A](label: String)(fa: F[A])(implicit F: Sync[F]): F[A] = {
      val label1 = if (label.nonEmpty) s" $label:" else ""
      infoTimed { duration =>
        string.lineLeftRight(s"Total time:$label1 ${dateTime.showDuration(duration)}")
      }(fa)
    }
  }

  def showUpdates[F[_]: Foldable: Functor](updates: F[Update]): String = {
    val list = updates.map(u => "  " + u.show).foldSmash("", "\n", "")
    updates.size match {
      case 0 => s"Found 0 updates"
      case 1 => s"Found 1 update:\n$list"
      case n => s"Found $n updates:\n$list"
    }
  }
}
