/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.dependency.json

import better.files.File
import cats.MonadError
import cats.implicits._
import eu.timepit.scalasteward.application.WorkspaceService
import eu.timepit.scalasteward.dependency.{Dependency, DependencyRepository}
import eu.timepit.scalasteward.git.Sha1
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.FileService
import io.circe.parser.decode
import io.circe.syntax._

class JsonDependencyRepository[F[_]](
    fileService: FileService[F],
    workspaceService: WorkspaceService[F]
)(implicit F: MonadError[F, Throwable])
    extends DependencyRepository[F] {

  override def findSha1(repo: Repo): F[Option[Sha1]] =
    readJson.map(_.store.get(repo).map(_.sha1))

  override def setDependencies(repo: Repo, sha1: Sha1, dependencies: List[Dependency]): F[Unit] =
    readJson.flatMap { store =>
      val updated = store.store.updated(repo, RepoValues(sha1, dependencies))
      writeJson(Store(updated))
    }

  def jsonFile: F[File] =
    workspaceService.root.map(_ / "repos.json")

  def readJson: F[Store] =
    jsonFile.flatMap { file =>
      fileService.readFile(file).flatMap { content =>
        F.fromEither(decode[Store](content))
      }
    }

  def writeJson(store: Store): F[Unit] =
    jsonFile.flatMap { file =>
      fileService.writeFile(file, store.asJson.toString)
    }
}
