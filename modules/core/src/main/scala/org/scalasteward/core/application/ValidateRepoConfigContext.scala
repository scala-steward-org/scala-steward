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
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ValidateRepoConfigContext {
  def run[F[_]](repoConfigFile: File)(implicit F: Sync[F]): F[ExitCode] =
    for {
      logger <- Slf4jLogger.fromName[F]("org.scalasteward.core")
      fileAlg = FileAlg.create(logger, F)
      validateRepoConfigAlg = new ValidateRepoConfigAlg()(fileAlg, logger, F)
      exitCode <- validateRepoConfigAlg.validateAndReport(repoConfigFile)
    } yield exitCode
}
