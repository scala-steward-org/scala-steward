/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.util

import cats.implicits._
import io.chrisdavenport.log4cats.Logger

object logger {
  implicit final class LoggerOps[F[_]](val self: Logger[F]) {
    def attemptLog[A](message: String)(fa: F[A])(
        implicit F: MonadThrowable[F]
    ): F[Either[Throwable, A]] =
      self.info(message) >> fa.attempt.flatTap {
        case Left(t)  => self.error(t)(s"$message failed")
        case Right(_) => F.unit
      }

    def attemptLog_[A](message: String)(fa: F[A])(implicit F: MonadThrowable[F]): F[Unit] =
      attemptLog(message)(fa).void
  }
}
