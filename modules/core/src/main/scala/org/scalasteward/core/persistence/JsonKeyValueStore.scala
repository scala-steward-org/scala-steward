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
import io.circe.{Decoder, Encoder, KeyEncoder}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util.MonadThrowable

final class JsonKeyValueStore[F[_], K, V](name: String, schemaVersion: String)(
    implicit
    fileAlg: FileAlg[F],
    keyEncoder: KeyEncoder[K],
    valueDecoder: Decoder[V],
    valueEncoder: Encoder[V],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends KeyValueStore[F, K, V] {
  override def get(key: K): F[Option[V]] =
    jsonFile(key).flatMap(fileAlg.readFile).flatMap {
      case Some(content) => F.fromEither(decode[Option[V]](content))
      case None          => F.pure(Option.empty[V])
    }

  override def put(key: K, value: V): F[Unit] =
    write(key, Some(value))

  override def modifyF(key: K)(f: Option[V] => F[Option[V]]): F[Option[V]] =
    get(key).flatMap(maybeValue => f(maybeValue).flatTap(write(key, _)))

  private def jsonFile(key: K): F[File] =
    workspaceAlg.rootDir.map(
      _ / "store" / s"${name}_v${schemaVersion}" / keyEncoder(key) / s"$name.json"
    )

  private def write(key: K, value: Option[V]): F[Unit] =
    jsonFile(key).flatMap(fileAlg.writeFile(_, value.asJson.toString))
}
