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

import cats.effect.{Async, Resource}
import cats.implicits._
import com.github.benmanes.caffeine.cache.Caffeine
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.{Method, Request, Status, Uri}
import org.scalasteward.core.application.Config
import scalacache.CatsEffect.modes._
import scalacache.caffeine.CaffeineCache
import scalacache.{Async => _, _}

final class HttpExistenceClient[F[_]](statusCache: Cache[Status])(implicit
    client: Client[F],
    logger: Logger[F],
    mode: Mode[F],
    F: MonadThrowable[F]
) {
  def exists(uri: Uri): F[Boolean] =
    status(uri).map(_ === Status.Ok).handleErrorWith { throwable =>
      logger.debug(throwable)(s"Failed to check if $uri exists").as(false)
    }

  private def status(uri: Uri): F[Status] =
    statusCache.cachingForMemoizeF(uri.renderString)(None) {
      client.status(Request[F](method = Method.HEAD, uri = uri))
    }
}

object HttpExistenceClient {
  def create[F[_]](implicit
      config: Config,
      client: Client[F],
      logger: Logger[F],
      F: Async[F]
  ): Resource[F, HttpExistenceClient[F]] = {
    val buildCache = F.delay {
      CaffeineCache(
        Caffeine
          .newBuilder()
          .maximumSize(16384L)
          .expireAfterWrite(config.cacheTtl.length, config.cacheTtl.unit)
          .build[String, Entry[Status]]()
      )
    }
    Resource.make(buildCache)(_.close().void).map(new HttpExistenceClient[F](_))
  }
}
