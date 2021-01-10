/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.repoconfig

import better.files.File
import cats.MonadThrow
import cats.data.OptionT
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.circe.config.parser
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Update
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg._
import org.scalasteward.core.vcs.data.Repo

final class RepoConfigAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def mergeWithDefault(maybeRepoConfig: Option[RepoConfig]): F[RepoConfig] =
    readDefaultRepoConfig.map { maybeDefault =>
      (maybeRepoConfig |+| maybeDefault).getOrElse(RepoConfig.empty)
    }

  private val readDefaultRepoConfig: F[Option[RepoConfig]] =
    config.defaultRepoConfigFile.flatTraverse(readRepoConfigFromFile(_).value)

  def readRepoConfig(repo: Repo): F[Option[RepoConfig]] =
    workspaceAlg
      .repoDir(repo)
      .flatMap(dir => readRepoConfigFromFile(dir / repoConfigBasename).value)

  private def readRepoConfigFromFile(configFile: File): OptionT[F, RepoConfig] =
    OptionT(fileAlg.readFile(configFile)).map(parseRepoConfig).flatMapF {
      case Right(repoConfig) => logger.info(s"Parsed $repoConfig").as(repoConfig.some)
      case Left(errorMsg)    => logger.info(errorMsg).as(none[RepoConfig])
    }
}

object RepoConfigAlg {
  val repoConfigBasename: String = ".scala-steward.conf"

  def parseRepoConfig(input: String): Either[String, RepoConfig] =
    parser.decode[RepoConfig](input).leftMap { error =>
      s"Failed to parse $repoConfigBasename: ${error.getMessage}"
    }

  def configToIgnoreFurtherUpdates(update: Update): String =
    update match {
      case s: Update.Single =>
        s"""updates.ignore = [ { groupId = "${s.groupId}", artifactId = "${s.artifactId.name}" } ]"""
      case g: Update.Group =>
        s"""updates.ignore = [ { groupId = "${g.groupId}" } ]"""
    }
}
