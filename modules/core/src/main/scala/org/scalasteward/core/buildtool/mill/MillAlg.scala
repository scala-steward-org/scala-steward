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

package org.scalasteward.core.buildtool.mill

import cats.syntax.all._
import org.scalasteward.core.BuildInfo
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.data.Scope
import org.scalasteward.core.data.Scope.Dependencies
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.{MonadThrow, Nel}
import org.scalasteward.core.vcs.data.Repo

trait MillAlg[F[_]] extends BuildToolAlg[F]

object MillAlg {
  private val content =
    s"""|import coursierapi.MavenRepository
        |
        |interp.repositories() ++= Seq(
        |  MavenRepository.of("https://oss.sonatype.org/content/repositories/snapshots/")
        |)
        |interp.load.ivy("${BuildInfo.organization}" %% "${BuildInfo.millPluginModuleName}" % "${BuildInfo.version}")
        |""".stripMargin

  def create[F[_]](implicit
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: MonadThrow[F]
  ): MillAlg[F] =
    new MillAlg[F] {
      override def containsBuild(repo: Repo): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap(repoDir => fileAlg.isRegularFile(repoDir / "build.sc"))

      override def getDependencies(repo: Repo): F[List[Dependencies]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          predef = repoDir / "scala-steward.sc"
          _ <- fileAlg.writeFile(predef, content)
          extractDeps = s"${BuildInfo.millPluginModuleRootPkg}.StewardPlugin/extractDeps"
          extracted <- processAlg.execSandboxed(
            Nel("mill", List("-i", "-p", predef.toString(), "show", extractDeps)),
            repoDir
          )
          parsed <- F.fromEither(
            parser.parseModules(extracted.dropWhile(!_.startsWith("{")).mkString("\n"))
          )
          _ <- fileAlg.deleteForce(predef)
        } yield parsed.map(module => Scope(module.dependencies, module.repositories))

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] = F.unit
    }
}
