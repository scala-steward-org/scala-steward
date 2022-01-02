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
import cats.syntax.all._
import io.circe.config.parser
import org.scalasteward.core.data.Update
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg._
import org.scalasteward.core.vcs.data.Repo
import org.typelevel.log4cats.Logger

final class RepoConfigAlg[F[_]](maybeGlobalRepoConfig: Option[RepoConfig])(implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def mergeWithGlobal(maybeRepoConfig: Option[RepoConfig]): RepoConfig =
    (maybeRepoConfig |+| maybeGlobalRepoConfig).getOrElse(RepoConfig.empty)

  def readRepoConfig(repo: Repo): F[ConfigParsingResult] =
    workspaceAlg
      .repoDir(repo)
      .flatMap(dir => readRepoConfigFromFile(dir / repoConfigBasename))

  private def readRepoConfigFromFile(configFile: File): F[ConfigParsingResult] = {
    for {
      parsedConfig <- fileAlg.readFile(configFile).map(_.map(parseRepoConfig))
      _ <- parsedConfig.fold(F.unit)(_.fold(logger.info(_), repoConfig => logger.info(s"Parsed repo config ${repoConfig.show}")))
    } yield parsedConfig
  }
}

object RepoConfigAlg {

  // None stands for the non-existing config file.
  // Otherwise, you got either a config error, either parsed config.
  type ConfigParsingResult = Option[Either[String, RepoConfig]]

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
