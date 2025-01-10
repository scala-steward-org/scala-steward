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

package org.scalasteward.core.buildtool.maven

import better.files.File
import cats.effect.{MonadCancelThrow, Resource}
import cats.syntax.all.*
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.*
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class MavenAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    override protected val logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadCancelThrow[F]
) extends BuildToolAlg[F] {
  override def name: String = "Maven"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg
      .buildRootDir(buildRoot)
      .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / pomXmlName))

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      dependenciesRaw <- exec(
        mvnCmd(command.listDependencies, args.excludeTransitive),
        buildRootDir
      )
      repositoriesRaw <- exec(
        mvnCmd(command.listRepositories),
        buildRootDir
      )
      dependencies = parser.parseDependencies(dependenciesRaw).distinct
      resolvers = parser.parseResolvers(repositoriesRaw).distinct
    } yield List(Scope(dependencies, resolvers))

  override protected val scalafixIssue: Option[String] =
    Some("https://github.com/scala-steward-org/scala-steward/issues/2839")

  private def exec(command: Nel[String], repoDir: File): F[List[String]] =
    maybeIgnoreOptsFiles(repoDir).surround(processAlg.execSandboxed(command, repoDir))

  private def mvnCmd(commands: String*): Nel[String] =
    Nel("mvn", args.batchMode :: commands.toList)

  private def maybeIgnoreOptsFiles(dir: File): Resource[F, Unit] =
    if (config.ignoreOptsFiles) ignoreOptsFiles(dir) else Resource.unit[F]

  private def ignoreOptsFiles(dir: File): Resource[F, Unit] =
    fileAlg.removeTemporarily(dir / ".jvmopts")
}
