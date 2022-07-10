/*
 * Copyright 2018-2022 Scala Steward contributors
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
import cats.effect.ExitCode
import org.typelevel.log4cats.Logger
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg

import ValidateRepoConfigAlg._

final class ValidateRepoConfigAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    monadThrowF: MonadThrow[F]
) {

  def validateConfigFile(configFile: File): F[ConfigValidationResult] =
    fileAlg.readFile(configFile).map {
      case Some(content) => validateContent(content)
      case None          => ConfigValidationResult.FileDoesNotExist
    }

  def validateAndReport(configFile: File): F[ExitCode] =
    validateConfigFile(configFile).flatMap { result =>
      ValidateRepoConfigAlg.presentValidationResult(configFile)(result) match {
        case Left(errMsg) => logger.error(errMsg).as(ExitCode.Error)
        case Right(okMsg) => logger.info(okMsg).as(ExitCode.Success)
      }
    }
}

object ValidateRepoConfigAlg {
  sealed trait ConfigValidationResult

  object ConfigValidationResult {
    case object FileDoesNotExist extends ConfigValidationResult
    case class ConfigIsInvalid(err: io.circe.Error) extends ConfigValidationResult
    case object Ok extends ConfigValidationResult
  }

  def validateContent(content: String): ConfigValidationResult =
    RepoConfigAlg.parseRepoConfig(content) match {
      case Left(err) => ConfigValidationResult.ConfigIsInvalid(err)
      case Right(_)  => ConfigValidationResult.Ok
    }

  def presentValidationResult(
      configFile: File
  )(result: ConfigValidationResult): Either[String, String] =
    result match {
      case ConfigValidationResult.Ok =>
        s"Configuration file at $configFile is valid.".asRight
      case ConfigValidationResult.FileDoesNotExist =>
        s"Configuration file at $configFile does not exist!".asLeft
      case ConfigValidationResult.ConfigIsInvalid(err) =>
        s"Configuration file at $configFile contains errors:\n  $err".asLeft
    }
}
