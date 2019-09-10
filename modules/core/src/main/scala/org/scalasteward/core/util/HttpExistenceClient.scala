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

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.{Method, Request, Status, Uri}

final class HttpExistenceClient[F[_]](
    implicit
    client: Client[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def exists(uri: String): F[Boolean] = F.fromEither(Uri.fromString(uri)).flatMap(exists)

  def exists(uri: Uri): F[Boolean] = {
    val req = Request[F](method = Method.HEAD, uri = uri)
    client.status(req).map(_ === Status.Ok).handleErrorWith { throwable =>
      logger.debug(throwable)(s"Failed to check if $uri exists").as(false)
    }
  }
}
