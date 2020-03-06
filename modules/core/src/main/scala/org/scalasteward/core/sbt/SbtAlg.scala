/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.sbt

import better.files.File
import cats.data.OptionT
import cats.implicits._
import cats.{Functor, Monad}
import org.scalasteward.core.application.Config
import org.scalasteward.core.build.system.BuildSystemAlg
import org.scalasteward.core.data.{Dependency, Resolver, Scope}
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.sbt.command._
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

object SbtAlg {
  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      scalafmtAlg: ScalafmtAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): BuildSystemAlg[F] =
    new BuildSystemAlg[F] {

      final def getSbtDependency(repo: Repo)(implicit F: Functor[F]): F[Option[Dependency]] =
        OptionT(getSbtVersion(repo)).subflatMap(sbtDependency).value

      def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A] =
        sbtDir.flatMap { dir =>
          val plugins = "plugins"
          fileAlg.createTemporarily(dir / "0.13" / plugins / plugin.name, plugin.content) {
            fileAlg.createTemporarily(dir / "1.0" / plugins / plugin.name, plugin.content) {
              fa
            }
          }
        }

      def getSbtVersion(repo: Repo): F[Option[SbtVersion]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          maybeProperties <- fileAlg.readFile(repoDir / "project" / "build.properties")
          version = maybeProperties.flatMap(parser.parseBuildProperties)
        } yield version

//      override def getDependencies(repo: Repo): F[List[Dependency]] =
//        for {
//          repoDir <- workspaceAlg.repoDir(repo)
//          cmd = sbtCmd(List(crossStewardDependencies, reloadPlugins, stewardDependencies))
//          lines <- exec(cmd, repoDir)
//          dependencies = parser.parseDependencies(lines)
//          maybeSbtDependency <- getSbtDependency(repo)
//          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
//        } yield (maybeSbtDependency.toList ++ maybeScalafmtDependency.toList ++ dependencies).distinct


      override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          commands = List(setOffline, crossStewardDependencies, reloadPlugins, stewardDependencies)
          lines <- exec(sbtCmd(commands), repoDir)
          dependencies = parser.parseDependencies(lines)
          additionalDependencies <- getAdditionalDependencies(repo)
          // combine scopes with the same resolvers
          result = (dependencies ++ additionalDependencies)
            .groupByNel(_.resolvers)
            .values
            .toList
            .map(group => group.head.as(group.reduceMap(_.value).distinct.sorted))
        } yield result

//      override def getUpdatesForRepo(repo: Repo): F[List[Update.Single]] =
//        for {
//          repoDir <- workspaceAlg.repoDir(repo)
//          commands = List(setOffline, crossStewardDependencies, reloadPlugins, stewardDependencies)
//          lines <- exec(sbtCmd(commands), repoDir)
//          dependencies = parser.parseDependencies(lines)
//          additionalDependencies <- getAdditionalDependencies(repo)
//          // combine scopes with the same resolvers
//          result = (dependencies ++ additionalDependencies)
//            .groupByNel(_.resolvers)
//            .values
//            .toList
//            .map(group => group.head.as(group.reduceMap(_.value).distinct.sorted))
//        } yield result

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        addGlobalPluginTemporarily(scalaStewardScalafixSbt) {
          for {
            repoDir <- workspaceAlg.repoDir(repo)
            scalafixCmds = for {
              migration <- migrations
              rule <- migration.rewriteRules
              cmd <- Nel.of(scalafix, testScalafix)
            } yield s"$cmd $rule"
            _ <- exec(sbtCmd(scalafixEnable :: scalafixCmds.toList), repoDir)
          } yield ()
        }

      val sbtDir: F[File] =
        fileAlg.home.map(_ / ".sbt")

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def sbtCmd(commands: List[String]): Nel[String] =
        Nel.of("sbt", "-batch", "-no-colors", commands.mkString(";", ";", ""))

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts") {
          fileAlg.removeTemporarily(dir / ".sbtopts") {
            fa
          }
        }

      def getAdditionalDependencies(repo: Repo): F[List[Scope.Dependencies]] =
        for {
          maybeSbtDependency <- getSbtDependency(repo)
          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
        } yield Nel
          .fromList(maybeSbtDependency.toList ++ maybeScalafmtDependency.toList)
          .map(dependencies => Scope(dependencies.toList, List(Resolver.mavenCentral)))
          .toList
    }
}
