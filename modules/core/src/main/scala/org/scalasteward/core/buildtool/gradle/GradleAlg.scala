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

package org.scalasteward.core.buildtool.gradle

import cats.Monad
import cats.syntax.all._
import org.scalasteward.core.buildtool.bsp.{BspExtractor, BspServerType}
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.Scope.Dependencies
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.typelevel.log4cats.Logger

final class GradleAlg[F[_]](implicit
    bspExtractor: BspExtractor[F],
    fileAlg: FileAlg[F],
    override protected val logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) extends BuildToolAlg[F] {
  override def enabled: Boolean = false

  override def name: String = "Gradle"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg.buildRootDir(buildRoot).flatMap { buildRootDir =>
      List("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
        .existsM(name => fileAlg.isRegularFile(buildRootDir / name))
    }

  override def getDependencies(buildRoot: BuildRoot): F[List[Dependencies]] =
    bspExtractor.getDependencies(BspServerType.Gradle, buildRoot)
}
