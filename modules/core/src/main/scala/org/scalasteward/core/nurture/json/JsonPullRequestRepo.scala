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

package org.scalasteward.core.nurture.json

import better.files.File
import cats.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.Uri
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.update.UpdateService
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.vcs.data.{PullRequestState, Repo}

class JsonPullRequestRepo[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends PullRequestRepository[F] {
  override def createOrUpdate(
      repo: Repo,
      url: Uri,
      baseSha1: Sha1,
      update: Update,
      state: PullRequestState
  ): F[Unit] =
    readJson.flatMap { store =>
      val updated = store.store.get(repo) match {
        case Some(prs) => prs.updated(url.toString(), PullRequestData(baseSha1, update, state))
        case None      => Map(url.toString() -> PullRequestData(baseSha1, update, state))
      }
      writeJson(PullRequestStore(store.store.updated(repo, updated)))
    }

  override def findPullRequest(
      repo: Repo,
      dependency: Dependency,
      newVersion: String
  ): F[Option[(Uri, Sha1, PullRequestState)]] =
    readJson.map { store =>
      val pullRequests = store.store.get(repo).fold(List.empty[(String, PullRequestData)])(_.toList)
      pullRequests
        .find {
          case (_, data) =>
            UpdateService.isUpdateFor(data.update, dependency) && data.update.nextVersion === newVersion
        }
        .map { case (url, data) => (Uri.unsafeFromString(url), data.baseSha1, data.state) }
    }

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "prs_v02.json")

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
