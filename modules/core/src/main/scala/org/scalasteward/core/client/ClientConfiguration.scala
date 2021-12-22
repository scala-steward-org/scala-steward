/*
 * Copyright 2018-2021 Scala Steward contributors
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

import org.http4s.client._
import org.http4s.headers.`User-Agent`
import cats.effect._
import org.http4s.okhttp.client.OkHttpBuilder

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

  def disableFollowRedirect[F[_]](builder: OkHttpBuilder[F]): OkHttpBuilder[F] =
    builder.withOkHttpClient(
      builder.okHttpClient.newBuilder().followRedirects(false).build()
    )
}
