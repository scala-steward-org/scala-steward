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

import cats.effect.concurrent.Semaphore
import cats.effect.{Concurrent, Resource, Timer}
import cats.implicits._
import com.github.benmanes.caffeine.cache.Caffeine
import scala.concurrent.duration._
import scalacache.CatsEffect.modes._
import scalacache.Entry
import scalacache.caffeine.CaffeineCache

trait RateLimiter[F[_]] {
  def limitUnseen[A](key: String)(fa: F[A]): F[A]
}

object RateLimiter {
  def create[F[_]](implicit timer: Timer[F], F: Concurrent[F]): Resource[F, RateLimiter[F]] =
    for {
      cache <- Resource.make(F.delay {
        CaffeineCache(Caffeine.newBuilder().maximumSize(65536L).build[String, Entry[Unit]]())
      })(_.close().void)
      semaphore <- Resource.liftF(Semaphore(1))
    } yield new RateLimiter[F] {
      override def limitUnseen[A](key: String)(fa: F[A]): F[A] =
        cache.get(key).flatMap {
          case Some(_) => fa
          case None    => semaphore.withPermit(timer.sleep(250.millis) *> fa <* cache.put(key)(()))
        }
    }
}
