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
import org.scalasteward.core.update.PruningAlg
import org.scalasteward.core.util.DateTimeAlg
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data.Repo

final class StewardAlg[F[_]](
    implicit
    config: Config,
    dateTimeAlg: DateTimeAlg[F],
    fileAlg: FileAlg[F],
    logger: Logger[F],
    nurtureAlg: NurtureAlg[F],
    pruningAlg: PruningAlg[F],
    repoCacheAlg: RepoCacheAlg[F],
    sbtAlg: SbtAlg[F],
    selfCheckAlg: SelfCheckAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def printBanner: F[Unit] = {
    val banner =
      """|  ____            _         ____  _                             _
         | / ___|  ___ __ _| | __ _  / ___|| |_ _____      ____ _ _ __ __| |
         | \___ \ / __/ _` | |/ _` | \___ \| __/ _ \ \ /\ / / _` | '__/ _` |
         |  ___) | (_| (_| | | (_| |  ___) | ||  __/\ V  V / (_| | | | (_| |
         | |____/ \___\__,_|_|\__,_| |____/ \__\___| \_/\_/ \__,_|_|  \__,_|""".stripMargin
    val msg = List(" ", banner, s" v${org.scalasteward.core.BuildInfo.version}", " ")
      .mkString(System.lineSeparator())
    logger.info(msg)
  }

  def prepareEnv: F[Unit] =
    for {
      _ <- sbtAlg.addGlobalPlugins
      _ <- workspaceAlg.cleanWorkspace
    } yield ()

  def readRepos(reposFile: File): F[List[Repo]] =
    fileAlg.readFile(reposFile).map { maybeContent =>
      val regex = """-\s+(.+)/([^/]+)""".r
      val content = maybeContent.getOrElse("")
      content.linesIterator.collect { case regex(owner, repo) => Repo(owner.trim, repo.trim) }.toList
    }

  def pruneRepos(repos: List[Repo]): F[List[Repo]] =
    logger.infoTotalTime("pruning repos") {
      for {
        _ <- repos.traverse_(repoCacheAlg.checkCache)
        allUpdates <- pruningAlg.checkForUpdates(repos)
        filteredRepos <- pruningAlg.filterByApplicableUpdates(repos, allUpdates)
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
    logger.infoTotalTime("run") {
      for {
        _ <- printBanner
        _ <- selfCheckAlg.checkAll
        _ <- prepareEnv
        repos <- readRepos(config.reposFile)
        reposToNurture <- if (config.pruneRepos) pruneRepos(repos) else F.pure(repos)
        result <- reposToNurture.traverse(nurtureAlg.nurture)
      } yield if (result.forall(_.isRight)) ExitCode.Success else ExitCode.Error
    }
}
