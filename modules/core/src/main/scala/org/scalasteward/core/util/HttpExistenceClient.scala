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

import cats.effect.Sync
import cats.implicits._
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

final class HttpExistenceClient[F[_]: Sync](
    implicit client: Client[F]
) {
  def exists(uri: String): F[Boolean] = exists(Uri.unsafeFromString(uri))

  def exists(uri: Uri): F[Boolean] = {
    val req: Request[F] = Request(method = Method.HEAD, uri = uri)
    client.status(req).map(_.isSuccess)
  }
}
