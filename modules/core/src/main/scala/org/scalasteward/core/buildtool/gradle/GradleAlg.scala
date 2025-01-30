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

package org.scalasteward.core.buildtool.gradle

import better.files.File
import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.Scope.Dependencies
import org.scalasteward.core.data.{Resolver, Scope}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.typelevel.log4cats.Logger

final class GradleAlg[F[_]](defaultResolvers: List[Resolver])(implicit
    fileAlg: FileAlg[F],
    override protected val logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) extends BuildToolAlg[F] {
  override def name: String = "Gradle"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    libsVersionsToml(buildRoot).flatMap(fileAlg.isRegularFile)

  override def getDependencies(buildRoot: BuildRoot): F[List[Dependencies]] =
    libsVersionsToml(buildRoot)
      .flatMap(fileAlg.readFile)
      .map(_.getOrElse(""))
      .map(gradleParser.parseDependenciesAndPlugins)
      .map { case (dependencies, plugins) =>
        val ds = Option.when(dependencies.nonEmpty)(Scope(dependencies, defaultResolvers))
        val ps = Option.when(plugins.nonEmpty)(Scope(plugins, List(pluginsResolver)))
        ds.toList ++ ps.toList
      }

  private def libsVersionsToml(buildRoot: BuildRoot): F[File] =
    workspaceAlg.buildRootDir(buildRoot).map(_ / "gradle" / libsVersionsTomlName)
}
