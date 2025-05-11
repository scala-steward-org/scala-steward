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
import org.http4s.headers.Location
import org.http4s.{Method, Request, Status, Uri}
import org.scalasteward.core.application.Config
import org.typelevel.log4cats.Logger
import scalacache.Entry
import scalacache.caffeine.CaffeineCache

trait UrlChecker[F[_]] {
  def validate(url: Uri): F[UrlChecker.UrlValidationResult]
}

final case class UrlCheckerClient[F[_]](client: Client[F]) extends AnyVal

object UrlChecker {

  sealed abstract class UrlValidationResult extends Product with Serializable {
    def exists: Boolean =
      fold(exists = true, notExists = false, redirectTo = _ => false)

    def notExists: Boolean =
      fold(exists = false, notExists = true, redirectTo = _ => false)

    def fold[A](exists: => A, notExists: => A, redirectTo: Uri => A): A =
      this match {
        case UrlValidationResult.Exists          => exists
        case UrlValidationResult.NotExists       => notExists
        case UrlValidationResult.RedirectTo(url) => redirectTo(url)
      }
  }

  object UrlValidationResult {
    case object Exists extends UrlValidationResult
    case object NotExists extends UrlValidationResult
    final case class RedirectTo(url: Uri) extends UrlValidationResult
  }

  private def buildCache[F[_]](config: Config)(implicit
      F: Sync[F]
  ): F[CaffeineCache[F, String, UrlValidationResult]] =
    F.delay {
      val cache = Caffeine
        .newBuilder()
        .maximumSize(16384L)
        .expireAfterWrite(config.cacheTtl.length, config.cacheTtl.unit)
        .build[String, Entry[UrlValidationResult]]()
      CaffeineCache(cache)
    }

  def create[F[_]](config: Config, modify: Request[F] => F[Request[F]])(implicit
      urlCheckerClient: UrlCheckerClient[F],
      logger: Logger[F],
      F: Sync[F]
  ): F[UrlChecker[F]] =
    buildCache(config).map { statusCache =>
      new UrlChecker[F] {
        override def validate(url: Uri): F[UrlValidationResult] =
          check(url).handleErrorWith(th =>
            logger
              .debug(th)(s"Failed to check if $url exists")
              .as(UrlValidationResult.NotExists)
          )

        /** While checking for the [[Uri]]s presence, we perform up to 3 recursive lookups when
          * receiving a `MovedPermanently` response.
          */
        private def check(url: Uri): F[UrlValidationResult] = {
          def lookup(url: Uri, maxDepth: Int): F[Option[Uri]] =
            Option(maxDepth).filter(_ > 0).flatTraverse { depth =>
              val req = Request[F](method = Method.HEAD, uri = url)

              modify(req).flatMap(r =>
                urlCheckerClient.client.run(r).use {
                  case resp if resp.status === Status.Ok =>
                    F.pure(url.some)

                  case resp if resp.status === Status.MovedPermanently =>
                    resp.headers
                      .get[Location]
                      .flatTraverse(location => lookup(location.uri, depth - 1))

                  case _ =>
                    F.pure(none)
                }
              )
            }

          statusCache.cachingF(url.renderString)(None) {
            lookup(url, maxDepth = 3).map {
              case Some(u) if u === url => UrlValidationResult.Exists
              case Some(u)              => UrlValidationResult.RedirectTo(u)
              case None                 => UrlValidationResult.NotExists
            }
          }
        }
      }
    }
}
