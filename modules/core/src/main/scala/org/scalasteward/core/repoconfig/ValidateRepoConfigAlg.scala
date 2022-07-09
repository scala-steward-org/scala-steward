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

import cats.MonadThrow
import cats.syntax.all._
import better.files.File
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import ValidateRepoConfigAlg._

final class ValidateRepoConfigAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    F: MonadThrow[F]
) {

  def validateConfigFile(configFile: File): F[ConfigValidationResult] =
    fileAlg.readFile(configFile).map {
      case Some(content) => validateContent(content)
      case None          => ConfigValidationResult.FileDoesNotExist
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
}
