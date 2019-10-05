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

package org.scalasteward.core.persistence

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util.MonadThrowable

final class JsonKeyValueStore[F[_], K, V](name: String, schemaVersion: String)(
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F],
    keyDecoder: KeyDecoder[K],
    keyEncoder: KeyEncoder[K],
    valueDecoder: Decoder[V],
    valueEncoder: Encoder[V]
) extends KeyValueStore[F, K, V] {

  override def get(key: K): F[Option[V]] =
    read.map(_.get(key))

  override def getMany(keys: List[K]): F[Map[K, V]] =
    read.map(_.filterKeys(keys.contains))

  override def modifyF(key: K)(f: Option[V] => F[Option[V]]): F[Option[V]] =
    read.flatMap { store =>
      f(store.get(key)).flatMap {
        case res @ Some(updated) => write(store.updated(key, updated)).as(res)
        case None                => write(store - key).as(None)
      }
    }

  private val filename =
    s"${name}_v${schemaVersion}.json"

  private val jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / filename)

  private def read: F[Map[K, V]] =
    jsonFile.flatMap(fileAlg.readFile).flatMap {
      case Some(content) => F.fromEither(decode[Map[K, V]](content))
      case None          => F.pure(Map.empty[K, V])
    }

  private def write(store: Map[K, V]): F[Unit] =
    jsonFile.flatMap(fileAlg.writeFile(_, store.asJson.toString))
}
