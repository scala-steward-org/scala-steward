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

package org.scalasteward.core.buildtool.mill

import better.files.File
import cats.effect.MonadCancelThrow
import cats.syntax.all.*
import org.scalasteward.core.buildtool.mill.MillAlg.*
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.*
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class MillAlg[F[_]](defaultResolvers: List[Resolver])(implicit
    fileAlg: FileAlg[F],
    override protected val logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadCancelThrow[F]
) extends BuildToolAlg[F] {
  override def name: String = "Mill"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg.buildRootDir(buildRoot).flatMap(findBuildFile).map(_.nonEmpty)

  private def findBuildFile(buildRootDir: File): F[Option[File]] =
    List("build.sc", "build.mill", "build.mill.scala")
      .map(buildRootDir / _)
      .findM(fileAlg.isRegularFile)

  private def runMill(buildRootDir: File, millBuildVersion: Option[Version]): F[List[String]] =
    millBuildVersion match {
      case Some(v) if v >= Version("0.11") =>
        val noTicker =
          if (v >= Version("0.12")) List("--ticker", "false") else List("--disable-ticker")
        val options =
          "--no-server" :: noTicker ++ List("--import", cliPluginCoordinate, "show", extractDeps)
        val command = Nel("mill", options)
        processAlg.execSandboxed(command, buildRootDir)
      case _ =>
        val predef = buildRootDir / "scala-steward.sc"
        val predefContent = content(millBuildVersion)
        val command = Nel("mill", List("-i", "-p", predef.toString, "show", extractDeps))
        fileAlg.createTemporarily(predef, predefContent).surround {
          processAlg.execSandboxed(command, buildRootDir)
        }
    }

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      millBuildVersion <- getMillVersion(buildRootDir)
      dependencies <- getProjectDependencies(buildRootDir, millBuildVersion)
      millBuildDeps = millBuildVersion.toSeq.map(version =>
        Scope(List(millMainArtifact(version)), defaultResolvers)
      )
      millPluginDeps <- millBuildVersion match {
        case None        => F.pure(Seq.empty[Scope[List[Dependency]]])
        case Some(value) => getMillPluginDeps(value, buildRootDir)
      }
    } yield dependencies ++ millBuildDeps ++ millPluginDeps

  private def getProjectDependencies(
      buildRootDir: File,
      millBuildVersion: Option[Version]
  ): F[List[Scope.Dependencies]] =
    for {
      extracted <- runMill(buildRootDir, millBuildVersion)
      parsed <- F.fromEither(
        parser.parseModules(extracted.dropWhile(!_.startsWith("{")).mkString("\n"))
      )
      dependencies = parsed.map(module => Scope(module.dependencies, module.repositories))
    } yield dependencies

  override protected val scalafixIssue: Option[String] =
    Some("https://github.com/scala-steward-org/scala-steward/issues/2838")

  private def getMillVersion(buildRootDir: File): F[Option[Version]] =
    List(
      buildRootDir / s".$millVersionName",
      buildRootDir / ".config" / millVersionName
    ).collectFirstSomeM(fileAlg.readFile).map(_.flatMap(parser.parseMillVersion))

  private def getMillPluginDeps(
      millVersion: Version,
      buildRootDir: File
  ): F[Seq[Scope[List[Dependency]]]] =
    for {
      buildFile <- findBuildFile(buildRootDir)
      buildContent <- buildFile.flatTraverse(fileAlg.readFile)
      deps = buildContent.toList.map(content =>
        Scope(parser.parseMillPluginDeps(content, millVersion), defaultResolvers)
      )
    } yield deps
}

object MillAlg {
  private[mill] val cliPluginCoordinate: String =
    s"ivy:${org.scalasteward.core.BuildInfo.organization}::${org.scalasteward.core.BuildInfo.millPluginArtifactName}::${org.scalasteward.core.BuildInfo.millPluginVersion}"

  private[mill] def content(millVersion: Option[Version]): String = {
    // We support these platforms, but we can't take the $MILL_BIN_PLATFORM support for granted
    val millBinPlatform = millVersion match {
      case Some(v) if v >= Version("0.10") => "$MILL_BIN_PLATFORM"
      case Some(v) if v >= Version("0.9")  => "0.9"
      case Some(v) if v >= Version("0.7")  => "0.7"
      case Some(v) if v >= Version("0.6")  => "0.6"
      case _                               => "$MILL_BIN_PLATFORM"
    }
    s"""import $$ivy.`${org.scalasteward.core.BuildInfo.organization}::${org.scalasteward.core.BuildInfo.millPluginArtifactName}_mill${millBinPlatform}:${org.scalasteward.core.BuildInfo.millPluginVersion}`"""
  }

  val extractDeps: String = "org.scalasteward.mill.plugin.StewardPlugin/extractDeps"

  private val millMainGroupId = GroupId("com.lihaoyi")
  private val millMainArtifactId = ArtifactId("mill-main", "mill-main_2.13")

  private def millMainArtifact(version: Version): Dependency =
    Dependency(millMainGroupId, millMainArtifactId, version)

  def isMillMainUpdate(update: Update.Single): Boolean =
    update.groupId === millMainGroupId &&
      update.artifactIds.exists(_.name === millMainArtifactId.name)

  val millVersionName = "mill-version"
}
