/*
 * Copyright 2018-2023 Scala Steward contributors
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
import cats.syntax.all._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import java.net.http.HttpClient
import java.net.http.HttpClient.Builder
import org.http4s.Response
import org.http4s.client._
import org.http4s.headers.`User-Agent`
import org.http4s.jdkhttpclient.JdkHttpClient
import org.typelevel.ci._
import scala.concurrent.duration._

object ClientConfiguration {
  type BuilderMiddleware = Builder => Builder

  object BuilderMiddleware {
    def default: BuilderMiddleware = _.followRedirects(HttpClient.Redirect.ALWAYS)
  }

  def build[F[_]: Async](bmw: BuilderMiddleware, cmw: Middleware[F]): Resource[F, Client[F]] =
    Resource
      .eval(defaultHttpClient[F](bmw).map(JdkHttpClient[F](_)))
      .map(cmw)

  // Copied from https://github.com/http4s/http4s-jdk-http-client/blob/b9655b90549319fbe999069e7c95ab1752efecb9/core/src/main/scala/org/http4s/jdkhttpclient/JdkHttpClient.scala#L257-L275 to support BuilderMiddleware
  private def defaultHttpClient[F[_]: Async](bmw: BuilderMiddleware): F[HttpClient] =
    Async[F].executor.flatMap { exec =>
      Async[F].delay {
        val builder = HttpClient.newBuilder()

        if (Runtime.version().feature() == 11) {
          val params = javax.net.ssl.SSLContext.getDefault().getDefaultSSLParameters()
          params.setProtocols(params.getProtocols().filter(_ != "TLSv1.3"))
          builder.sslParameters(params)
        }

        builder.executor(exec)

        bmw(builder).build()
      }
    }

  def setUserAgent[F[_]: MonadCancelThrow](userAgent: `User-Agent`): Middleware[F] = { client =>
    Client[F](req => client.run(req.putHeaders(userAgent)))
  }

  private val RetryAfterStatuses = Set(403, 429, 503)

  /** @param maxAttempts
    *   max number times the HTTP request should be sent useful to avoid unexpected cloud provider
    *   costs
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
          } yield Resource
            .eval(response.as[Unit].voidError *> Temporal[F].sleep(duration))
            .flatMap(_ => run(attempt + 1))
          maybeRetried.getOrElse(Resource.pure(response))
        }

      run()
    }
  }

  def disableFollowRedirect: BuilderMiddleware = _.followRedirects(HttpClient.Redirect.NEVER)
}
