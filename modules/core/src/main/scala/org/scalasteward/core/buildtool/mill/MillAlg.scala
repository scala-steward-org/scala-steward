/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.buildtool.mill

import better.files.File
import cats.effect.MonadCancelThrow
import cats.syntax.all._
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.buildtool.mill.MillAlg._
import org.scalasteward.core.data.Scope.Dependencies
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

final class MillAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadCancelThrow[F]
) extends BuildToolAlg[F] {
  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg
      .buildRootDir(buildRoot)
      .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sc"))

  override def getDependencies(buildRoot: BuildRoot): F[List[Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      predef = buildRootDir / "scala-steward.sc"
      extracted <- fileAlg.createTemporarily(predef, content).surround {
        val command = Nel("mill", List("-i", "-p", predef.toString, "show", extractDeps))
        processAlg.execSandboxed(command, buildRootDir)
      }
      jsonString = extracted.dropWhile(!_.startsWith("{")).mkString("\n")
      _ <- F.whenA(jsonString.isBlank) {
        val errorMessage =
          """|Couldn\'t extract dependencies JSON from sandboxed execution. You may need to increase the maximum
             |buffer size using the command line argument `max-buffer-size`.""".stripMargin
        logger.error(errorMessage) *> F.raiseError(new Throwable(errorMessage))
      }
      parsed <- F.fromEither(parser.parseModules(jsonString))
      dependencies = parsed.map(module => Scope(module.dependencies, module.repositories))
      millBuildVersion <- getMillVersion(buildRootDir)
      millBuildDeps = millBuildVersion.toSeq.map(version =>
        Scope(List(millMainArtifact(version)), List(millMainResolver))
      )
      millPluginDeps <- millBuildVersion match {
        case None        => F.pure(Seq.empty[Scope[List[Dependency]]])
        case Some(value) => getMillPluginDeps(value, buildRootDir)
      }
    } yield dependencies ++ millBuildDeps ++ millPluginDeps

  override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    F.unit

  private def getMillVersion(buildRootDir: File): F[Option[Version]] =
    for {
      millVersionFileContent <- fileAlg.readFile(buildRootDir / ".mill-version")
      version = millVersionFileContent.flatMap(parser.parseMillVersion)
    } yield version

  private def getMillPluginDeps(
      millVersion: Version,
      buildRootDir: File
  ): F[Seq[Scope[List[Dependency]]]] =
    for {
      buildConent <- fileAlg.readFile(buildRootDir / "build.sc")
      deps = buildConent.toList.map(content =>
        Scope(parser.parseMillPluginDeps(content, millVersion), List(millMainResolver))
      )
    } yield deps
}

object MillAlg {
  private val content =
    s"""|import coursierapi.MavenRepository
        |
        |interp.repositories() ++= Seq(
        |  MavenRepository.of("https://oss.sonatype.org/content/repositories/snapshots/")
        |)
        |interp.load.ivy("${org.scalasteward.core.BuildInfo.organization}" %% "${org.scalasteward.core.BuildInfo.millPluginModuleName}" % "${org.scalasteward.core.BuildInfo.version}")
        |""".stripMargin

  val extractDeps: String =
    s"${org.scalasteward.core.BuildInfo.millPluginModuleRootPkg}.StewardPlugin/extractDeps"

  private val millMainResolver: Resolver = Resolver.mavenCentral
  private val millMainGroupId = GroupId("com.lihaoyi")
  private val millMainArtifactId = ArtifactId("mill-main", "mill-main_2.13")

  private def millMainArtifact(version: Version): Dependency =
    Dependency(millMainGroupId, millMainArtifactId, version)

  def isMillMainUpdate(update: Update): Boolean =
    update.groupId === millMainGroupId && update.artifactIds.exists(
      _.name === millMainArtifactId.name
    )
}
