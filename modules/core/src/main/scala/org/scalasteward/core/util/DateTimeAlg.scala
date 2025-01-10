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

import cats.effect.Sync
import cats.syntax.all.*
import cats.{Functor, Monad}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait DateTimeAlg[F[_]] {
  def currentTimeMillis: F[Long]

  final def currentTimestamp(implicit F: Functor[F]): F[Timestamp] =
    currentTimeMillis.map(Timestamp.apply)

  final def timed[A](fa: F[A])(implicit F: Monad[F]): F[(A, FiniteDuration)] =
    for {
      start <- currentTimeMillis
      a <- fa
      end <- currentTimeMillis
      duration = FiniteDuration(end - start, TimeUnit.MILLISECONDS)
    } yield (a, duration)
}

object DateTimeAlg {
  def create[F[_]](implicit F: Sync[F]): DateTimeAlg[F] =
    new DateTimeAlg[F] {
      override def currentTimeMillis: F[Long] =
        F.delay(System.currentTimeMillis())
    }
}
