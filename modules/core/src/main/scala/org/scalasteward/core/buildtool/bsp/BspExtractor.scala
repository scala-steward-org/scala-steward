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

package org.scalasteward.core.buildtool.bsp

import better.files.File
import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import ch.epfl.scala.bsp4j.{
  BuildClientCapabilities,
  DependencyModulesParams,
  DependencyModulesResult,
  InitializeBuildParams
}
import io.circe.parser.decode
import java.util.Collections
import java.util.concurrent.CompletableFuture
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data._
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

final class BspExtractor[F[_]](defaultResolver: Resolver, processTimeout: FiniteDuration)(implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Async[F]
) {
  def getDependencies(
      bspServerType: BspServerType,
      buildRoot: BuildRoot
  ): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      connectionDetails <- createConnectionDetailsFile(bspServerType, buildRoot)
      details <- readConnectionDetailsFile(connectionDetails)
      bspDependencies <- getBspDependencies(buildRootDir, details)
    } yield transform(bspDependencies)

  private def createConnectionDetailsFile(
      bspServerType: BspServerType,
      buildRoot: BuildRoot
  ): F[File] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      _ <- processAlg.execSandboxed(bspServerType.connectionDetailsCommand, buildRootDir)
    } yield buildRootDir / ".bsp" / bspServerType.connectionDetailsName

  private def readConnectionDetailsFile(connectionDetailsFile: File): F[BspConnectionDetails] =
    fileAlg.readFile(connectionDetailsFile).flatMap {
      case Some(content) =>
        decode[BspConnectionDetails](content) match {
          case Right(connectionDetails) => F.pure(connectionDetails)
          case Left(error)              => F.raiseError(error)
        }
      case None =>
        F.raiseError(new Throwable(s"${connectionDetailsFile.pathAsString} does not exist"))
    }

  private def getBspDependencies(
      buildRootDir: File,
      details: BspConnectionDetails
  ): F[DependencyModulesResult] =
    BspProcess
      .run(details.argv, buildRootDir)
      .use { p =>
        val result = for {
          initBuildResult <- lift(p.buildInitialize(initBuildParams(buildRootDir)))
          _ <- F.blocking(p.onBuildInitialized())
          isDependencyModulesProvider =
            Option(initBuildResult.getCapabilities.getDependencyModulesProvider)
              .exists(_.booleanValue())
          _ <- F.raiseWhen(!isDependencyModulesProvider) {
            new Throwable(s"${initBuildResult.getDisplayName} is not a dependency modules provider")
          }
          buildTargetsResult <- lift(p.workspaceBuildTargets())
          dependencyModulesParams =
            new DependencyModulesParams(buildTargetsResult.getTargets.asScala.map(_.getId).asJava)
          dependencyModulesResult <- lift(p.buildTargetDependencyModules(dependencyModulesParams))
        } yield dependencyModulesResult
        result.guarantee(lift(p.buildShutdown()) >> F.blocking(p.onBuildExit()))
      }
      .timeoutAndForget(processTimeout)

  private def initBuildParams(buildRootDir: File): InitializeBuildParams =
    new InitializeBuildParams(
      "Scala Steward",
      org.scalasteward.core.BuildInfo.version,
      org.scalasteward.core.BuildInfo.bsp4jVersion,
      buildRootDir.uri.toString,
      new BuildClientCapabilities(Collections.emptyList())
    )

  private def lift[A](fut: => CompletableFuture[A]): F[A] =
    F.fromCompletableFuture(F.blocking(fut))

  private def transform(bspDependencies: DependencyModulesResult): List[Scope.Dependencies] =
    bspDependencies.getItems.asScala.toList.map { item =>
      val dependencies = item.getModules.asScala.toList.mapFilter { module =>
        module.getName.split(':') match {
          case Array(groupId, artifactId) =>
            val g = GroupId(groupId)
            val a = ArtifactId.from(artifactId)
            val v = Version(module.getVersion)
            Dependency(g, a, v).some
          case _ => None
        }
      }
      // The BSP does not yet provide resolvers, so we use the default resolver here.
      // See https://github.com/build-server-protocol/build-server-protocol/discussions/500
      // for a proposal to add resolvers to BSP.
      Scope(dependencies, List(defaultResolver))
    }
}
