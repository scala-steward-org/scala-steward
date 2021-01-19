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

package org.scalasteward.core.application

import cats.MonadThrow
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.scalafmt.{scalafmtBinary, ScalafmtAlg}
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.util.logger.LoggerOps

final class SelfCheckAlg[F[_]](implicit
    gitAlg: GitAlg[F],
    logger: Logger[F],
    scalafmtAlg: ScalafmtAlg[F],
    urlChecker: UrlChecker[F],
    F: MonadThrow[F]
) {
  def checkAll: F[Unit] =
    for {
      _ <- logger.info("Run self checks")
      _ <- checkGitBinary
      _ <- checkScalafmtBinary
      _ <- checkUrlChecker
    } yield ()

  private def checkGitBinary: F[Unit] =
    logger.attemptLogWarn_(execFailedMessage("git")) {
      gitAlg.version.flatMap(output => logger.info(s"Using $output"))
    }

  private def checkScalafmtBinary: F[Unit] =
    logger.attemptLogWarn_(execFailedMessage(scalafmtBinary)) {
      scalafmtAlg.version.flatMap(output => logger.info(s"Using $output"))
    }

  private def execFailedMessage(binary: String): String =
    s"Failed to execute $binary  -- make sure it is on the PATH; following the detailed exception:"

  private def checkUrlChecker: F[Unit] =
    for {
      url <- F.fromEither(Uri.fromString("https://github.com"))
      res <- urlChecker.exists(url)
      msg = s"Self check of UrlChecker failed: checking that $url exists failed"
      _ <- if (!res) logger.warn(msg) else F.unit
    } yield ()
}
