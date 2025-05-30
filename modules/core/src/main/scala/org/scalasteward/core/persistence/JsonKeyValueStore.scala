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

package org.scalasteward.core.persistence

import better.files.File
import cats.Monad
import cats.syntax.all.*
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, KeyEncoder}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.typelevel.log4cats.Logger

final class JsonKeyValueStore[F[_], K, V](storeRoot: File, name: String)(implicit
    fileAlg: FileAlg[F],
    keyEncoder: KeyEncoder[K],
    logger: Logger[F],
    valueDecoder: Decoder[V],
    valueEncoder: Encoder[V],
    F: Monad[F]
) extends KeyValueStore[F, K, V] {
  override def get(key: K): F[Option[V]] = {
    val file = jsonFile(key)
    fileAlg.readFile(file).flatMap {
      case None          => F.pure(Option.empty[V])
      case Some(content) =>
        decode[Option[V]](content) match {
          case Right(maybeValue) => F.pure(maybeValue)
          case Left(error)       =>
            logger.error(error)(s"Failed to parse or decode JSON from $file").as(Option.empty[V])
        }
    }
  }

  override def set(key: K, value: Option[V]): F[Unit] = {
    val file = jsonFile(key)
    value match {
      case Some(v) => fileAlg.writeFile(file, v.asJson.spaces2)
      case None    => fileAlg.deleteForce(file)
    }
  }

  private def jsonFile(key: K): File =
    storeRoot / keyEncoder(key) / s"$name.json"
}

object JsonKeyValueStore {
  def create[F[_], K, V](name: String, schemaVersion: String, maybePrefix: Option[String] = None)(
      implicit
      fileAlg: FileAlg[F],
      keyEncoder: KeyEncoder[K],
      logger: Logger[F],
      valueDecoder: Decoder[V],
      valueEncoder: Encoder[V],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): F[JsonKeyValueStore[F, K, V]] = {
    val prefix = maybePrefix.fold("")("/" + _)
    workspaceAlg.rootDir
      .map(_ / "store" / name / s"v$schemaVersion$prefix")
      .map(storeRoot => new JsonKeyValueStore(storeRoot, name))
  }
}
