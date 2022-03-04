/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.client

import cats.effect._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import org.http4s.Response
import org.http4s.client._
import org.http4s.headers.`User-Agent`
import org.http4s.okhttp.client.OkHttpBuilder
import org.typelevel.ci._

import scala.concurrent.duration._

object ClientConfiguration {
  type BuilderMiddleware[F[_]] = OkHttpBuilder[F] => OkHttpBuilder[F]
  object BuilderMiddleware {
    def default[F[_]]: BuilderMiddleware[F] = bmw => bmw
  }
  def build[F[_]: Async](bmw: BuilderMiddleware[F], cmw: Middleware[F]): Resource[F, Client[F]] =
    OkHttpBuilder
      .withDefaultClient[F]
      .map(bmw)
      .flatMap(_.resource)
      .map(cmw)

  def setUserAgent[F[_]: MonadCancelThrow](userAgent: `User-Agent`): Middleware[F] = { client =>
    Client[F](req => client.run(req.putHeaders(userAgent)))
  }

  private val RetryAfterStatuses = Set(403, 429, 503)

  /**
    * @param maxAttempts max number times the HTTP request should be sent
    *                    useful to avoid unexpected cloud provider costs
    */
  def retryAfter[F[_]: Temporal](maxAttempts: PosInt = 5): Middleware[F] = { client =>
    Client[F] { req =>
      def run(attempt: Int = 1): Resource[F, Response[F]] = client
        .run(req.putHeaders("X-Attempt" -> attempt.toString))
        .flatMap { response =>
          val maybeRetried = for {
            header <- response.headers.get(ci"Retry-After")
            seconds <- header.head.value.toIntOption
            if seconds > 0
            duration = seconds.seconds
            if RetryAfterStatuses.contains(response.status.code)
            if attempt < maxAttempts.value
          } yield Resource.eval(Temporal[F].sleep(duration)).flatMap(_ => run(attempt + 1))
          maybeRetried.getOrElse(Resource.pure(response))
        }

      run()
    }
  }

  def disableFollowRedirect[F[_]](builder: OkHttpBuilder[F]): OkHttpBuilder[F] =
    builder.withOkHttpClient(
      builder.okHttpClient.newBuilder().followRedirects(false).build()
    )
}
