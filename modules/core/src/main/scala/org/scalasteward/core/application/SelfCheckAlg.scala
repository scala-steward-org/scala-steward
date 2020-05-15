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

package org.scalasteward.core.application

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.util.{HttpExistenceClient, MonadThrowable}

final class SelfCheckAlg[F[_]](implicit
    httpExistenceClient: HttpExistenceClient[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def checkAll: F[Unit] =
    for {
      _ <- logger.info("Run self checks")
      _ <- checkHttpExistenceClient
    } yield ()

  private def checkHttpExistenceClient: F[Unit] =
    for {
      url <- F.fromEither(Uri.fromString("https://github.com"))
      res <- httpExistenceClient.exists(url)
      msg = s"Self check of HttpExistenceClient failed: checking that $url exists failed"
      _ <- if (!res) logger.warn(msg) else F.unit
    } yield ()
}
