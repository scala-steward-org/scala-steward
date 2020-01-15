/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.effect.concurrent.Semaphore
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import scala.concurrent.duration._

trait RateLimiter[F[_]] {
  def limit[A](fa: F[A]): F[A]
}

object RateLimiter {
  def create[F[_]](implicit timer: Timer[F], F: Concurrent[F]): F[RateLimiter[F]] =
    Semaphore(1).map { semaphore =>
      new RateLimiter[F] {
        override def limit[A](fa: F[A]): F[A] =
          semaphore.withPermit(timer.sleep(300.millis) >> fa)
      }
    }
}
