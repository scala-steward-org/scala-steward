/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.application

import cats.effect.{ConcurrentEffect, Resource}
import cats.implicits._
import eu.timepit.scalasteward.dependency.DependencyService
import eu.timepit.scalasteward.dependency.json.JsonDependencyRepository
import eu.timepit.scalasteward.git.GitAlg
import eu.timepit.scalasteward.github.GitHubService
import eu.timepit.scalasteward.github.http4s.Http4sGitHubService
import eu.timepit.scalasteward.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import eu.timepit.scalasteward.sbt.SbtAlg
import eu.timepit.scalasteward.update.UpdateService
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.ExecutionContext

final case class Context[F[_]](
    config: Config,
    dependencyService: DependencyService[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    gitHubService: GitHubService[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    sbtAlg: SbtAlg[F],
    updateService: UpdateService[F],
    workspaceAlg: WorkspaceAlg[F]
)

object Context {
  def create[F[_]: ConcurrentEffect](config: Config): Resource[F, Context[F]] =
    BlazeClientBuilder[F](ExecutionContext.global).resource.flatMap { client =>
      Resource.liftF(
        for {
          logger <- Slf4jLogger.create[F]
          fileAlg = FileAlg.create[F]
          gitHubService = new Http4sGitHubService(client)
          processAlg = ProcessAlg.create[F]
          workspaceAlg = WorkspaceAlg.create(fileAlg, config.workspace)
          gitAlg = GitAlg.create[F](fileAlg, processAlg, workspaceAlg)
          sbtAlg = SbtAlg.create[F](fileAlg, logger, processAlg, workspaceAlg)
          dependencyRepository = new JsonDependencyRepository[F](fileAlg, workspaceAlg)
          dependencyService = new DependencyService[F](
            dependencyRepository,
            gitHubService,
            gitAlg,
            logger,
            sbtAlg
          )
          updateService = new UpdateService[F](dependencyRepository, sbtAlg)
        } yield
          Context(
            config,
            dependencyService,
            fileAlg,
            gitAlg,
            gitHubService,
            logger,
            processAlg,
            sbtAlg,
            updateService,
            workspaceAlg
          )
      )
    }
}
