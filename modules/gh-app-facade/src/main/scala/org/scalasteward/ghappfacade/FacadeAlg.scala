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

import cats.effect.{ExitCode, Sync}
import cats.syntax.all._
import fs2.Stream
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

final class FacadeAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    githubAppApiAlg: GitHubAppApiAlg[F],
    githubAuthAlg: GitHubAuthAlg[F],
    logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Sync[F]
) {
  private def getGitHubAppRepos(gitHubApp: GitHubApp): Stream[F, Repo] =
    Stream.evals[F, List, Repo] {
      for {
        jwt <- githubAuthAlg.createJWT(gitHubApp, 2.minutes)
        installations <- githubAppApiAlg.installations(jwt)
        repositories <- installations.traverse { installation =>
          githubAppApiAlg
            .accessToken(jwt, installation.id)
            .flatMap(token => githubAppApiAlg.repositories(token.token))
        }
        repos <- repositories.flatMap(_.repositories).flatTraverse { repo =>
          repo.full_name.split('/') match {
            case Array(owner, name) => F.pure(List(Repo(owner, name)))
            case _                  => logger.error(s"invalid repo $repo").as(List.empty[Repo])
          }
        }
      } yield repos
    }

  def runF(stewardArgs: List[String]): F[ExitCode] =
    ???
}
