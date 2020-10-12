/*
 * Copyright 2018-2020 Scala Steward contributors
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

import better.files.File
import cats.effect.ExitCode
import cats.syntax.all._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.NurtureAlg
import org.scalasteward.core.repocache.RepoCacheAlg
import org.scalasteward.core.update.PruningAlg
import org.scalasteward.core.util
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.util.{BracketThrow, DateTimeAlg}
import org.scalasteward.core.vcs.data.Repo

final class StewardAlg[F[_]](implicit
    config: Config,
    dateTimeAlg: DateTimeAlg[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    nurtureAlg: NurtureAlg[F],
    pruningAlg: PruningAlg[F],
    repoCacheAlg: RepoCacheAlg[F],
    sbtAlg: SbtAlg[F],
    selfCheckAlg: SelfCheckAlg[F],
    streamCompiler: Stream.Compiler[F, F],
    workspaceAlg: WorkspaceAlg[F],
    F: BracketThrow[F]
) {
  private def readRepos(reposFile: File): Stream[F, Repo] =
    Stream.evals {
      fileAlg.readFile(reposFile).map { maybeContent =>
        val regex = """-\s+(.+)/([^/]+)""".r
        val content = maybeContent.getOrElse("")
        content.linesIterator.collect { case regex(owner, repo) =>
          Repo(owner.trim, repo.trim)
        }.toList
      }
    }

  private def steward(repo: Repo): F[Either[Throwable, Unit]] = {
    val label = s"Steward ${repo.show}"
    logger.infoTotalTime(label) {
      logger.attemptLog(util.string.lineLeftRight(label), Some(label)) {
        F.guarantee {
          for {
            fork <- repoCacheAlg.checkCache(repo)
            (attentionNeeded, updates) <- pruningAlg.needsAttention(repo)
            _ <- if (attentionNeeded) nurtureAlg.nurture(repo, fork, updates) else F.unit
          } yield ()
        }(gitAlg.removeClone(repo))
      }
    }
  }

  def runF: F[ExitCode] =
    logger.infoTotalTime("run") {
      for {
        _ <- selfCheckAlg.checkAll
        _ <- workspaceAlg.cleanWorkspace
        exitCode <- sbtAlg.addGlobalPlugins {
          readRepos(config.reposFile)
            .evalMap(steward)
            .compile
            .foldMonoid
            .map(_.fold(_ => ExitCode.Error, _ => ExitCode.Success))
        }
      } yield exitCode
    }
}
