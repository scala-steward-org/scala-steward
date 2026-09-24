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

package org.scalasteward.core.buildtool.giter8

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class Giter8Alg[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  private val templateDir = "src/main/g8"
  private val renderedDir = "target/g8"

  def getRenderedGiter8BuildRoot(repo: Repo): F[Option[BuildRoot]] =
    workspaceAlg.repoDir(repo).flatMap { repoDir =>
      fileAlg
        .isDirectory(repoDir / templateDir)
        .ifM(render(repo, repoDir), none[BuildRoot].pure[F])
    }

  private def render(repo: Repo, repoDir: better.files.File): F[Option[BuildRoot]] = {
    val renderedBuildRoot = BuildRoot(repo, renderedDir)
    val renderedBuildFile = repoDir / renderedDir / "build.sbt"

    logger.info(s"Render Giter8 template in $templateDir") >>
      sbtAlg.runSbt(Nel.one("g8"), repoDir).void >>
      fileAlg
        .isRegularFile(renderedBuildFile)
        .ifM(
          renderedBuildRoot.some.pure[F],
          logger
            .warn(s"Rendered Giter8 template does not contain $renderedDir/build.sbt")
            .as(none[BuildRoot])
        )
  }
}
