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

package org.scalasteward.core.repocache.json

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repocache.{RepoCache, RepoCacheRepository}
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.vcs.data.Repo

final class JsonRepoCacheRepository[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends RepoCacheRepository[F] {
  override def findCache(repo: Repo): F[Option[RepoCache]] =
    readJson.map(_.store.get(repo))

  override def updateCache(repo: Repo, repoCache: RepoCache): F[Unit] =
    readJson.flatMap { store =>
      writeJson(RepoStore(store.store.updated(repo, repoCache)))
    }

  override def getDependencies(repos: List[Repo]): F[List[Dependency]] =
    readJson.map(_.store.filterKeys(repos.contains).values.flatMap(_.dependencies).toList.distinct)

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "repos_v06.json")

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
