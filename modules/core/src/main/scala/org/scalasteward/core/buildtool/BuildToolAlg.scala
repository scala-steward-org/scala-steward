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

package org.scalasteward.core.buildtool

import org.scalasteward.core.data.Scope
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.typelevel.log4cats.Logger
import scala.annotation.nowarn

trait BuildToolAlg[F[_]] {
  def name: String

  def containsBuild(buildRoot: BuildRoot): F[Boolean]

  def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]]

  def runMigration(@nowarn buildRoot: BuildRoot, @nowarn migration: ScalafixMigration): F[Unit] =
    logger.warn(
      s"Scalafix migrations are currently not supported in $name projects" +
        scalafixIssue.fold("")(issue => s", see $issue for details")
    )

  protected def logger: Logger[F]

  protected def scalafixIssue: Option[String] = None
}
