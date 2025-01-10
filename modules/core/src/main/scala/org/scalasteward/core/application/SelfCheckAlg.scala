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

import cats.MonadThrow
import cats.syntax.all.*
import org.scalasteward.core.edit.scalafix.ScalafixCli
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.scalafmt.{scalafmtBinary, ScalafmtAlg}
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.util.logger.LoggerOps
import org.typelevel.log4cats.Logger

final class SelfCheckAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    scalafixCli: ScalafixCli[F],
    scalafmtAlg: ScalafmtAlg[F],
    urlChecker: UrlChecker[F],
    F: MonadThrow[F]
) {
  def checkAll: F[Unit] =
    for {
      _ <- logger.info("Run self checks")
      _ <- checkWorkspaceDirectory
      _ <- checkGitBinary
      _ <- checkScalafixBinary
      _ <- checkScalafmtBinary
      _ <- checkUrlChecker
    } yield ()

  private def checkWorkspaceDirectory: F[Unit] =
    fileAlg
      .isNonEmptyDirectory(config.workspace)
      .ifM(
        F.unit,
        logger.warn(
          s"""
             |The workspace directory is empty: '${config.workspace}'
             |This is expected if this is your first Scala Steward run.
             |Make sure to preserve the workspace between runs for all features to work as expected.
             |https://github.com/scala-steward-org/scala-steward/blob/main/docs/faq.md#why-doesnt-self-hosted-scala-steward-close-obsolete-prs
             |""".stripMargin
        )
      )

  private def checkGitBinary: F[Unit] =
    logger.attemptWarn.log_(execFailedMessage("git")) {
      gitAlg.version.flatMap(output => logger.info(s"Using $output"))
    }

  private def checkScalafixBinary: F[Unit] =
    logger.attemptWarn.log_(execFailedMessage(scalafixBinary)) {
      scalafixCli.version.flatMap(output => logger.info(s"Using $scalafixBinary $output"))
    }

  private def checkScalafmtBinary: F[Unit] =
    logger.attemptWarn.log_(execFailedMessage(scalafmtBinary)) {
      scalafmtAlg.version.flatMap(output => logger.info(s"Using $output"))
    }

  private def execFailedMessage(binary: String): String =
    s"Failed to execute $binary  -- make sure it is on the PATH; following the detailed exception:"

  private def checkUrlChecker: F[Unit] =
    config.urlCheckerTestUrls.traverse_ { url =>
      urlChecker.exists(url).flatMap { urlExists =>
        val msg = s"Self check of UrlChecker failed: checking that $url exists failed"
        F.whenA(!urlExists)(logger.warn(msg))
      }
    }
}
