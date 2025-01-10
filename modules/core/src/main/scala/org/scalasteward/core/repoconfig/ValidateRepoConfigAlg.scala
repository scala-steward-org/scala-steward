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

package org.scalasteward.core.repoconfig

import better.files.File
import cats.MonadThrow
import cats.effect.ExitCode
import cats.syntax.all.*
import io.circe.{CursorOp, DecodingFailure, ParsingFailure}
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg.ConfigParsingResult
import org.typelevel.log4cats.Logger

final class ValidateRepoConfigAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def validateAndReport(configFile: File): F[ExitCode] =
    RepoConfigAlg.readRepoConfigFromFile(configFile).flatMap { result =>
      ValidateRepoConfigAlg.presentValidationResult(configFile)(result) match {
        case Left(errMsg) => logger.error(errMsg).as(ExitCode.Error)
        case Right(okMsg) => logger.info(okMsg).as(ExitCode.Success)
      }
    }
}

object ValidateRepoConfigAlg {
  private def printCirceError(indent: String)(err: io.circe.Error): String =
    err match {
      case d: DecodingFailure =>
        val history =
          d.history
            .map {
              case CursorOp.DownField(k) => k
              case CursorOp.Field(k)     => k
              case other                 => other
            }
            .mkString(s"${indent * 3}\n")
        s"""|${indent}Decoding failed with:
            |${indent * 2}${d.message}:
            |${indent * 3}${history}""".stripMargin
      case ParsingFailure(message, _) =>
        s"""|${indent}Parsing failed with "$message".""".stripMargin
    }

  private def presentValidationResult(
      configFile: File
  )(result: ConfigParsingResult): Either[String, String] =
    result match {
      case ConfigParsingResult.Ok(_) =>
        s"Configuration file at $configFile is valid.".asRight
      case ConfigParsingResult.FileDoesNotExist =>
        s"Configuration file at $configFile does not exist!".asLeft
      case ConfigParsingResult.ConfigIsInvalid(err) =>
        s"""|Configuration file at $configFile contains errors:
            |${printCirceError(" " * 2)(err)}""".stripMargin.asLeft
    }
}
