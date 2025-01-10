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

package org.scalasteward.core.application

import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class ReposFilesLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: Sync[F]
) {
  def loadAll(reposFiles: Nel[Uri]): Stream[F, Repo] =
    Stream.emits(reposFiles.toList).evalMap(loadRepos).flatMap(Stream.emits)

  private def loadRepos(reposFile: Uri): F[List[Repo]] =
    for {
      _ <- logger.debug(s"Loading repos from $reposFile")
      content <- fileAlg.readUri(reposFile)
      repos <- Stream.fromIterator(content.linesIterator, 4096).mapFilter(Repo.parse).compile.toList
      _ <-
        if (repos.nonEmpty) F.unit
        else {
          val msg = s"No repos found in ${reposFile.renderString}. " +
            s"Check the formatting of that file. " +
            s"""The format is "- $$owner/$$repo" or "- $$owner/$$repo:$$branch"."""
          logger.warn(msg)
        }
    } yield repos
}
