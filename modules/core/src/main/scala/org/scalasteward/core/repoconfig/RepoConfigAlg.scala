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

package org.scalasteward.core.repoconfig

import better.files.File
import cats.syntax.all._
import cats.{Functor, Monad, MonadThrow}
import io.circe.config.parser
import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg._
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
    for {
      repoDir <- workspaceAlg.repoDir(repo)
      activeConfigFile <- activeConfigFile(repoDir)
      configParsingResult <- activeConfigFile.fold(
        F.pure[ConfigParsingResult](ConfigParsingResult.FileDoesNotExist)
      )(readRepoConfigFromFile(_))
      _ <- configParsingResult.fold(
        F.unit,
        error => logger.info(s"Failed to parse $activeConfigFile: ${error.getMessage}"),
        repoConfig => logger.info(s"Parsed repo config ${repoConfig.show}")
      )
    } yield configParsingResult
}

object RepoConfigAlg {
  sealed trait ConfigParsingResult {
    final def fold[A](
        onFileDoesNotExist: => A,
        onConfigIsInvalid: io.circe.Error => A,
        onOk: RepoConfig => A
    ): A =
      this match {
        case ConfigParsingResult.FileDoesNotExist       => onFileDoesNotExist
        case ConfigParsingResult.ConfigIsInvalid(error) => onConfigIsInvalid(error)
        case ConfigParsingResult.Ok(repoConfig)         => onOk(repoConfig)
      }

    final def maybeRepoConfig: Option[RepoConfig] =
      fold(None, _ => None, Some.apply)

    final def maybeParsingError: Option[io.circe.Error] =
      fold(None, Some.apply, _ => None)
  }

  object ConfigParsingResult {
    case object FileDoesNotExist extends ConfigParsingResult
    final case class ConfigIsInvalid(error: io.circe.Error) extends ConfigParsingResult
    final case class Ok(repoConfig: RepoConfig) extends ConfigParsingResult
  }

  val repoConfigBasename: String = ".scala-steward.conf"

  def parseRepoConfig(input: String): Either[io.circe.Error, RepoConfig] =
    parser.decode[RepoConfig](input)

  private val repoConfigFileSearchPath: List[List[String]] =
    List(List.empty, List(".github"), List(".config"))

  private def activeConfigFile[F[_]](
      repoDir: File
  )(implicit fileAlg: FileAlg[F], logger: Logger[F], F: Monad[F]): F[Option[File]] = {
    val configFileCandidates: F[List[File]] = (repoConfigFileSearchPath
      .map(_ :+ repoConfigBasename) ++
      repoConfigFileSearchPath
        .map(_ :+ repoConfigBasename.substring(1)))
      .map(path => path.foldLeft(repoDir)(_ / _))
      .filterA(fileAlg.isRegularFile)

    configFileCandidates.flatMap {
      case Nil => F.pure(None)
      case active :: remaining =>
        F.pure(active.some)
          .productL(
            remaining.traverse_(file =>
              logger.warn(s"""Ignored config file "${file.pathAsString}"""")
            )
          )
    }
  }

  def readRepoConfigFromFile[F[_]](
      configFile: File
  )(implicit fileAlg: FileAlg[F], F: Functor[F]): F[ConfigParsingResult] =
    fileAlg.readFile(configFile).map {
      case None => ConfigParsingResult.FileDoesNotExist
      case Some(content) =>
        parseRepoConfig(content) match {
          case Left(error)       => ConfigParsingResult.ConfigIsInvalid(error)
          case Right(repoConfig) => ConfigParsingResult.Ok(repoConfig)
        }
    }

  def configToIgnoreFurtherUpdates(update: Update): String = {
    val forUpdate: Update.Single => String = {
      case s: Update.ForArtifactId =>
        s"""{ groupId = "${s.groupId}", artifactId = "${s.artifactId.name}" }""".stripMargin
      case g: Update.ForGroupId =>
        s"""{ groupId = "${g.groupId}" }""".stripMargin
    }

    update.on(
      update = u => s"updates.ignore = [ ${forUpdate(u)} ]",
      grouped = g =>
        g.updates
          .map("  " + forUpdate(_))
          .mkString("updates.ignore = [\n", ",\n", "\n]")
    )
  }
}
