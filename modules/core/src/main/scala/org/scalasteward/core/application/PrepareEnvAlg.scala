/*
 * Copyright 2018-2019 Scala Steward contributors
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
import cats.Monad
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.io.{FileAlg, FileData, WorkspaceAlg}
import org.scalasteward.core.sbt.{scalaStewardSbt, stewardPlugin}

final class PrepareEnvAlg[F[_]](
    implicit workspaceAlg: WorkspaceAlg[F],
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: Monad[F]
) {

  def prepareEnv: F[Unit] =
    for {
      _ <- addGlobalPlugins
      _ <- workspaceAlg.cleanWorkspace
    } yield ()

  def addGlobalPlugins: F[Unit] =
    for {
      _ <- logger.info("Add global sbt plugins")
      _ <- addGlobalPlugin(scalaStewardSbt)
      _ <- addGlobalPlugin(stewardPlugin)
    } yield ()

  def addGlobalPlugin(plugin: FileData): F[Unit] =
    List("0.13", "1.0").traverse_ { series =>
      sbtDir.flatMap(dir => fileAlg.writeFileData(dir / series / "plugins", plugin))
    }

  val sbtDir: F[File] =
    fileAlg.home.map(_ / ".sbt")

}
