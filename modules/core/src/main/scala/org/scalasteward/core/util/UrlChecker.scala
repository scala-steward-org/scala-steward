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
import com.github.benmanes.caffeine.cache.Caffeine
import org.http4s.client.Client
import org.http4s.{Method, Request, Status, Uri}
import org.scalasteward.core.application.Config
import org.typelevel.log4cats.Logger
import scalacache.Entry
import scalacache.caffeine.CaffeineCache

trait UrlChecker[F[_]] {
  def exists(url: Uri): F[Boolean]
}

final case class UrlCheckerClient[F[_]](client: Client[F]) extends AnyVal

object UrlChecker {
  private def buildCache[F[_]](config: Config)(implicit
      F: Sync[F]
  ): F[CaffeineCache[F, String, Status]] =
    F.delay {
      val cache = Caffeine
        .newBuilder()
        .maximumSize(16384L)
        .expireAfterWrite(config.cacheTtl.length, config.cacheTtl.unit)
        .build[String, Entry[Status]]()
      CaffeineCache(cache)
    }

  def create[F[_]](config: Config, modify: Request[F] => F[Request[F]])(implicit
      urlCheckerClient: UrlCheckerClient[F],
      logger: Logger[F],
      F: Sync[F]
  ): F[UrlChecker[F]] =
    buildCache(config).map { statusCache =>
      new UrlChecker[F] {
        override def exists(url: Uri): F[Boolean] =
          status(url).map(_ === Status.Ok).handleErrorWith { throwable =>
            logger.debug(throwable)(s"Failed to check if $url exists").as(false)
          }

        private def status(url: Uri): F[Status] =
          statusCache.cachingF(url.renderString)(None) {
            val req = Request[F](method = Method.HEAD, uri = url)
            modify(req).flatMap(urlCheckerClient.client.status)
          }
      }
    }
}
