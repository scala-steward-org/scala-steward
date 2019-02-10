/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.update.json

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.update.UpdateRepository
import org.scalasteward.core.util.MonadThrowable

// WIP
class JsonUpdateRepository[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends UpdateRepository[F] {

  override def deleteAll: F[Unit] =
    writeJson(UpdateStore(List.empty))

  override def save(update: Update.Single): F[Unit] =
    readJson.map(s => UpdateStore((update :: s.store).distinct)).flatMap(writeJson)

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "updates_v02.json")

  def readJson: F[UpdateStore] =
    jsonFile.flatMap { file =>
      fileAlg.readFile(file).flatMap {
        case Some(content) => F.fromEither(decode[UpdateStore](content))
        case None          => F.pure(UpdateStore(List.empty))
      }
    }

  def writeJson(store: UpdateStore): F[Unit] =
    jsonFile.flatMap { file =>
      fileAlg.writeFile(file, store.asJson.toString)
    }
}
