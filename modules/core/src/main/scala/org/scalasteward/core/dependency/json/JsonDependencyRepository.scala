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

package org.scalasteward.core.dependency.json

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalasteward.core.dependency.{Dependency, DependencyRepository}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util.MonadThrowable

class JsonDependencyRepository[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends DependencyRepository[F] {

  override def findSha1(repo: Repo): F[Option[Sha1]] =
    readJson.map(_.store.get(repo).map(_.sha1))

  override def getDependencies(repos: List[Repo]): F[List[Dependency]] =
    readJson.map(_.store.filterKeys(repos.contains).values.flatMap(_.dependencies).toList.distinct)

  override def setDependencies(repo: Repo, sha1: Sha1, dependencies: List[Dependency]): F[Unit] =
    readJson.flatMap { store =>
      val updated = store.store.updated(repo, RepoData(sha1, dependencies))
      writeJson(RepoStore(updated))
    }

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "repos_v04.json")

  def readJson: F[RepoStore] =
    jsonFile.flatMap { file =>
      fileAlg.readFile(file).flatMap {
        case Some(content) => F.fromEither(decode[RepoStore](content))
        case None          => F.pure(RepoStore(Map.empty))
      }
    }

  def writeJson(store: RepoStore): F[Unit] =
    jsonFile.flatMap { file =>
      fileAlg.writeFile(file, store.asJson.toString)
    }
}
