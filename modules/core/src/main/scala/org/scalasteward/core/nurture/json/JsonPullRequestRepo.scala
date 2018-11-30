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

package org.scalasteward.core.nurture.json

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.Uri
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.util.MonadThrowable

class JsonPullRequestRepo[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends PullRequestRepository[F] {
  override def createOrUpdate(repo: Repo, url: Uri, baseSha1: Sha1, update: Update): F[Unit] =
    readJson.flatMap { store =>
      val updated = store.store.get(repo) match {
        case Some(prs) => prs.updated(url.toString(), PullRequestData(baseSha1, update))
        case None      => Map(url.toString() -> PullRequestData(baseSha1, update))
      }
      writeJson(PullRequestStore(store.store.updated(repo, updated)))
    }

  override def findUpdates(repo: Repo, baseSha1: Sha1): F[List[Update]] =
    readJson.map { store =>
      store.store.get(repo).fold(List.empty[Update]) { data =>
        data.values.filter(_.baseSha1 == baseSha1).map(_.update).toList
      }
    }

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "prs_v01.json")

  def readJson: F[PullRequestStore] =
    jsonFile.flatMap { file =>
      fileAlg.readFile(file).flatMap {
        case Some(content) => F.fromEither(decode[PullRequestStore](content))
        case None          => F.pure(PullRequestStore(Map.empty))
      }
    }

  def writeJson(store: PullRequestStore): F[Unit] =
    jsonFile.flatMap { file =>
      fileAlg.writeFile(file, store.asJson.toString)
    }
}
