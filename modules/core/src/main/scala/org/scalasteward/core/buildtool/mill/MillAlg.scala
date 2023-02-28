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

package org.scalasteward.core.buildtool.mill

import better.files.File
import cats.effect.MonadCancelThrow
import cats.syntax.all._
import org.scalasteward.core.buildtool.mill.MillAlg._
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class MillAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadCancelThrow[F]
) extends BuildToolAlg[F] {
  override def name: String = "Mill"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg
      .buildRootDir(buildRoot)
      .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sc"))

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      predef = buildRootDir / "scala-steward.sc"
      millBuildVersion <- getMillVersion(buildRootDir)
      extracted <- fileAlg.createTemporarily(predef, content(millBuildVersion)).surround {
        val command = Nel("mill", List("-i", "-p", predef.toString, "show", extractDeps))
        processAlg.execSandboxed(command, buildRootDir)
      }
      parsed <- F.fromEither(
        parser.parseModules(extracted.dropWhile(!_.startsWith("{")).mkString("\n"))
      )
      dependencies = parsed.map(module => Scope(module.dependencies, module.repositories))
      millBuildDeps = millBuildVersion.toSeq.map(version =>
        Scope(List(millMainArtifact(version)), List(millMainResolver))
      )
      millPluginDeps <- millBuildVersion match {
        case None        => F.pure(Seq.empty[Scope[List[Dependency]]])
        case Some(value) => getMillPluginDeps(value, buildRootDir)
      }
    } yield dependencies ++ millBuildDeps ++ millPluginDeps

  override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    logger.warn(
      s"Scalafix migrations are currently not supported in $name projects, see https://github.com/scala-steward-org/scala-steward/issues/2838 for details"
    )

  private def getMillVersion(buildRootDir: File): F[Option[Version]] =
    for {
      millVersionFileContent <- fileAlg.readFile(buildRootDir / millVersionName)
      millVersionFileInConfigContent <- fileAlg.readFile(
        buildRootDir / ".config" / millVersionNameInConfig
      )
      version = millVersionFileContent
        .orElse(millVersionFileInConfigContent)
        .flatMap(parser.parseMillVersion)
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
  private[mill] def content(millVersion: Option[Version]) = {
    def rawContent(millBinPlatform: String) =
      s"""|import $$ivy.`${org.scalasteward.core.BuildInfo.organization}::${org.scalasteward.core.BuildInfo.millPluginArtifactName}_mill${millBinPlatform}:${org.scalasteward.core.BuildInfo.millPluginVersion}`
          |""".stripMargin

    millVersion match {
      case None => rawContent("$MILL_BIN_PLATFORM")
      case Some(millVersion) =>
        millVersion.value.trim.split("[.]", 3).take(2) match {
          // We support these platforms, but we can't take the $MILL_BIN_PLATFORM support for granted
          case Array("0", "6")       => rawContent("0.6")
          case Array("0", "7" | "8") => rawContent("0.7")
          case Array("0", "9")       => rawContent("0.9")
          case _                     => rawContent("$MILL_BIN_PLATFORM")
        }
    }
  }

  val extractDeps: String = "org.scalasteward.mill.plugin.StewardPlugin/extractDeps"

  private val millMainResolver: Resolver = Resolver.mavenCentral
  private val millMainGroupId = GroupId("com.lihaoyi")
  private val millMainArtifactId = ArtifactId("mill-main", "mill-main_2.13")

  private def millMainArtifact(version: Version): Dependency =
    Dependency(millMainGroupId, millMainArtifactId, version)

  def isMillMainUpdate(update: Update.Single): Boolean =
    update.groupId === millMainGroupId &&
      update.artifactIds.exists(_.name === millMainArtifactId.name)

  val millVersionName = ".mill-version"
  val millVersionNameInConfig = "mill-version"
}
