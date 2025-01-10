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

package org.scalasteward.core.application

import cats.effect.Sync
import cats.syntax.all.*
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final class ValidateRepoConfigContext[F[_]](implicit
    val validateRepoConfigAlg: ValidateRepoConfigAlg[F]
)

object ValidateRepoConfigContext {
  def step0[F[_]](implicit F: Sync[F]): F[ValidateRepoConfigContext[F]] =
    for {
      logger <- Slf4jLogger.fromName[F]("org.scalasteward.core")
      fileAlg = FileAlg.create(logger, F)
      context = step1(fileAlg, logger, F)
    } yield context

  def step1[F[_]](implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      F: Sync[F]
  ): ValidateRepoConfigContext[F] = {
    implicit val validateRepoConfigAlg: ValidateRepoConfigAlg[F] = new ValidateRepoConfigAlg
    new ValidateRepoConfigContext[F]
  }
}
