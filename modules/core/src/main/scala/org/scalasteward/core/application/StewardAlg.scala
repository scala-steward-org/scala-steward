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

package org.scalasteward.core.application

import better.files.File
import cats.Monad
import cats.effect.ExitCode
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.NurtureAlg
import org.scalasteward.core.repocache.RepoCacheAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.UpdateService
import org.scalasteward.core.util.LogAlg
import org.scalasteward.core.vcs.data.Repo

import scala.util.Try

final class StewardAlg[F[_]](
    implicit
    config: Config,
    fileAlg: FileAlg[F],
    logAlg: LogAlg[F],
    logger: Logger[F],
    nurtureAlg: NurtureAlg[F],
    repoCacheAlg: RepoCacheAlg[F],
    sbtAlg: SbtAlg[F],
    updateService: UpdateService[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def prepareEnv: F[Unit] =
    for {
      _ <- sbtAlg.addGlobalPlugins
      _ <- workspaceAlg.cleanWorkspace
    } yield ()

  def readRepos(reposFile: File): F[List[Repo]] =
    fileAlg.readFile(reposFile).map { maybeContent =>
      val regex = """-\s+(.+)/([^/]+)""".r
      val regexWithProjectId = """-\s+(.+)/([^/]+)/pid:(.+)$""".r
      val content = maybeContent.getOrElse("")
      content.linesIterator.collect {
        case regexWithProjectId(owner, repo, pid) if config.vcsType === SupportedVCS.Gitlab =>
          Repo(
            owner.trim,
            repo.trim,
            Try(pid.toLong).toOption.orElse(throw new IllegalArgumentException(
              s"illegal PID for ${owner.trim}/${repo.trim} : not a number")))
        case regex(owner, repo) =>
          Repo(owner.trim, repo.trim)
      }.toList
    }

  def pruneRepos(repos: List[Repo]): F[List[Repo]] =
    logAlg.infoTotalTime("pruning repos") {
      for {
        _ <- repos.traverse(repoCacheAlg.checkCache)
        allUpdates <- updateService.checkForUpdates(repos)
        filteredRepos <- updateService.filterByApplicableUpdates(repos, allUpdates)
        countTotal = repos.size
        countFiltered = filteredRepos.size
        countPruned = countTotal - countFiltered
        _ <- logger.info(s"""Repos count:
                            |  total    = $countTotal
                            |  filtered = $countFiltered
                            |  pruned   = $countPruned""".stripMargin)
      } yield filteredRepos
    }

  def runF: F[ExitCode] =
    logAlg.infoTotalTime("run") {
      for {
        _ <- prepareEnv
        repos <- readRepos(config.reposFile)
        reposToNurture <- if (config.pruneRepos) pruneRepos(repos) else F.pure(repos)
        result <- reposToNurture.traverse(nurtureAlg.nurture)
      } yield if (result.forall(_.isRight)) ExitCode.Success else ExitCode.Error
    }
}
