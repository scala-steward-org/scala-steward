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

package org.scalasteward.core.forge

import cats.effect.Sync
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType.*
import org.scalasteward.core.forge.bitbucketserver.BitbucketServerAuthAlg
import org.scalasteward.core.forge.github.GitHubAuthAlg
import org.scalasteward.core.forge.gitlab.GitLabAuthAlg
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger

trait ForgeAuthAlg[F[_]] {
  def authenticateApi(req: Request[F]): F[Request[F]]
  def authenticateGit(uri: Uri): F[Uri]
  def accessibleRepos: F[List[Repo]]
}

object ForgeAuthAlg {
  def create[F[_]](config: Config)(implicit
      F: Sync[F],
      client: HttpJsonClient[F],
      workspaceAlg: WorkspaceAlg[F],
      processAlg: ProcessAlg[F],
      logger: Logger[F]
  ): ForgeAuthAlg[F] =
    config.forgeCfg.tpe match {
      case AzureRepos =>
        new BasicAuthAlg(config.forgeCfg.apiHost, config.forgeCfg.login, config.gitCfg.gitAskPass)
      case Bitbucket =>
        new BasicAuthAlg(config.forgeCfg.apiHost, config.forgeCfg.login, config.gitCfg.gitAskPass)
      case BitbucketServer =>
        new BitbucketServerAuthAlg(
          config.forgeCfg.apiHost,
          config.forgeCfg.login,
          config.gitCfg.gitAskPass
        )
      case GitHub =>
        config.githubApp match {
          case Some(gitHub) => new GitHubAuthAlg(config.forgeCfg.apiHost, gitHub.id, gitHub.keyFile)
          case None         =>
            new BasicAuthAlg(
              config.forgeCfg.apiHost,
              config.forgeCfg.login,
              config.gitCfg.gitAskPass
            )
        }
      case GitLab =>
        new GitLabAuthAlg(config.forgeCfg.apiHost, config.forgeCfg.login, config.gitCfg.gitAskPass)
      case Gitea =>
        new BasicAuthAlg(config.forgeCfg.apiHost, config.forgeCfg.login, config.gitCfg.gitAskPass)
    }
}
