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

package eu.timepit.scalasteward

import cats.implicits._
import cats.{ApplicativeError, Monad, MonadError}

package object util {
  type ApplicativeThrowable[F[_]] = ApplicativeError[F, Throwable]

  type MonadThrowable[F[_]] = MonadError[F, Throwable]

  def ifTrue[F[_]](fb: F[Boolean])(f: F[Unit])(implicit F: Monad[F]): F[Unit] =
    fb.ifM(f, F.unit)
}
