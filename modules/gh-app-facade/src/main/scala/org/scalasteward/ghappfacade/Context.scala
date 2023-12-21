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

package org.scalasteward.ghappfacade

import cats.effect._
import eu.timepit.refined.auto._
import org.http4s.client.Client
import org.http4s.headers.`User-Agent`
import org.scalasteward.core.client.ClientConfiguration
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final class Context[F[_]](implicit
    val facadeAlg: FacadeAlg[F],
    val fileAlg: FileAlg[F],
    val httpJsonClient: HttpJsonClient[F],
    val logger: Logger[F],
    val workspaceAlg: WorkspaceAlg[F]
)

object Context {
  def step0[F[_]](config: Config)(implicit F: Async[F]): Resource[F, Context[F]] =
    for {
      logger <- Resource.eval(Slf4jLogger.fromName[F]("org.scalasteward.ghappfacade"))
      userAgent <- Resource.eval(F.fromEither(`User-Agent`.parse(1)(userAgentString)))
      middleware = ClientConfiguration
        .setUserAgent[F](userAgent)
        .andThen(ClientConfiguration.retryAfter[F](maxAttempts = 5))
      client <- ClientConfiguration.build(ClientConfiguration.BuilderMiddleware.default, middleware)
      fileAlg = FileAlg.create(logger, F)
      workspaceAlg = WorkspaceAlg.create(config.workspace)(fileAlg, logger, F)
      context = step1(config)(client, fileAlg, logger, workspaceAlg, F)
    } yield context

  private def step1[F[_]](config: Config)(implicit
      client: Client[F],
      fileAlg: FileAlg[F],
      logger: Logger[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Async[F]
  ): Context[F] = {
    implicit val gitHubAuthAlg: GitHubAuthAlg[F] = GitHubAuthAlg.create[F]
    implicit val httpJsonClient: HttpJsonClient[F] = new HttpJsonClient[F]
    implicit val gitHubAppApiAlg: GitHubAppApiAlg[F] =
      new GitHubAppApiAlg[F](config.gitHubApiHost)
    implicit val facadeAlg: FacadeAlg[F] = new FacadeAlg[F](config)
    new Context[F]
  }

  private val userAgentString: String =
    s"Scala-Steward-GitHub-App-facade/${org.scalasteward.core.BuildInfo.version} (${org.scalasteward.core.BuildInfo.gitHubUrl})"
}
