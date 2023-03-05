/*
 * Copyright 2018-2023 Scala Steward contributors
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
import cats.effect.{ExitCode, Sync}
import cats.syntax.all._
import fs2.Stream
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.github.{GitHubApp, GitHubAppApiAlg, GitHubAuthAlg}
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.{NurtureAlg, PullRequestThrottle}
import org.scalasteward.core.repocache.RepoCacheAlg
import org.scalasteward.core.update.PruningAlg
import org.scalasteward.core.util
import org.scalasteward.core.util.DateTimeAlg
import org.scalasteward.core.util.logger.LoggerOps
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

final class StewardAlg[F[_]](config: Config)(implicit
    dateTimeAlg: DateTimeAlg[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    githubAppApiAlg: GitHubAppApiAlg[F],
    githubAuthAlg: GitHubAuthAlg[F],
    logger: Logger[F],
    nurtureAlg: NurtureAlg[F],
    pruningAlg: PruningAlg[F],
    pullRequestThrottle: PullRequestThrottle[F],
    repoCacheAlg: RepoCacheAlg[F],
    selfCheckAlg: SelfCheckAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Sync[F]
) {
  private def readRepos(reposFile: File): Stream[F, Repo] =
    Stream
      .eval(fileAlg.readFile(reposFile).map(_.getOrElse("")))
      .flatMap(content => Stream.fromIterator(content.linesIterator, 1024))
      .mapFilter(Repo.parse)

  private def getGitHubAppRepos(githubApp: GitHubApp): Stream[F, Repo] =
    Stream.evals[F, List, Repo] {
      for {
        jwt <- githubAuthAlg.createJWT(githubApp, 2.minutes)
        installations <- githubAppApiAlg.installations(jwt)
        repositories <- installations.traverse { installation =>
          githubAppApiAlg
            .accessToken(jwt, installation.id)
            .flatMap(token => githubAppApiAlg.repositories(token.token))
        }
        repos <- repositories.flatMap(_.repositories).flatTraverse { repo =>
          repo.full_name.split('/') match {
            case Array(owner, name) => F.pure(List(Repo(owner, name)))
            case _                  => logger.error(s"invalid repo $repo").as(List.empty[Repo])
          }
        }
      } yield repos
    }

  private def steward(repo: Repo): F[Either[Throwable, Unit]] = {
    val label = s"Steward ${repo.show}"
    logger.infoTotalTime(label) {
      logger.attemptError.label(util.string.lineLeftRight(label), Some(label)) {
        F.guarantee(
          repoCacheAlg.checkCache(repo).flatMap { case (data, fork) =>
            pruningAlg.needsAttention(data).flatMap {
              _.traverse_ { states =>
                pullRequestThrottle.throttle(nurtureAlg.nurture(data, fork, states.map(_.update)))
              }
            }
          },
          gitAlg.removeClone(repo)
        )
      }
    }
  }

  def runF: F[ExitCode] =
    logger.infoTotalTime("run") {
      for {
        _ <- selfCheckAlg.checkAll
        _ <- workspaceAlg.cleanReposDir
        exitCode <-
          (config.githubApp.map(getGitHubAppRepos).getOrElse(Stream.empty) ++
            readRepos(config.reposFile))
            .evalMap(steward)
            .compile
            .foldSemigroup
            .flatMap {
              case Some(result) => result.fold(_ => ExitCode.Error, _ => ExitCode.Success).pure[F]
              case None =>
                val msg = "No repos specified. " +
                  s"Check the formatting of ${config.reposFile.pathAsString}. " +
                  s"""The format is "- $$owner/$$repo" or "- $$owner/$$repo:$$branch"."""
                logger.warn(msg).as(ExitCode.Success)
            }
      } yield exitCode
    }
}
