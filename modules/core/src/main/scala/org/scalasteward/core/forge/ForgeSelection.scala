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

import cats.Parallel
import cats.effect.Temporal
import org.http4s.Request
import org.scalasteward.core.application.Config
import org.scalasteward.core.application.Config.{ForgeCfg, ForgeSpecificCfg}
import org.scalasteward.core.forge.azurerepos.AzureReposApiAlg
import org.scalasteward.core.forge.bitbucket.BitbucketApiAlg
import org.scalasteward.core.forge.bitbucketserver.BitbucketServerApiAlg
import org.scalasteward.core.forge.gitea.GiteaApiAlg
import org.scalasteward.core.forge.github.GitHubApiAlg
import org.scalasteward.core.forge.gitlab.GitLabApiAlg
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger

object ForgeSelection {
  def forgeApiAlg[F[_]: Parallel](
      forgeCfg: ForgeCfg,
      forgeSpecificCfg: ForgeSpecificCfg,
      auth: Request[F] => F[Request[F]]
  )(implicit
      httpJsonClient: HttpJsonClient[F],
      logger: Logger[F],
      F: Temporal[F]
  ): ForgeApiAlg[F] =
    forgeSpecificCfg match {
      case specificCfg: Config.AzureReposCfg =>
        new AzureReposApiAlg(forgeCfg.apiHost, specificCfg, auth)
      case specificCfg: Config.BitbucketCfg =>
        new BitbucketApiAlg(forgeCfg, specificCfg, auth)
      case specificCfg: Config.BitbucketServerCfg =>
        new BitbucketServerApiAlg(forgeCfg.apiHost, specificCfg, auth)
      case _: Config.GitHubCfg =>
        new GitHubApiAlg(forgeCfg.apiHost, auth)
      case specificCfg: Config.GitLabCfg =>
        new GitLabApiAlg(forgeCfg, specificCfg, auth)
      case _: Config.GiteaCfg =>
        new GiteaApiAlg(forgeCfg, auth)
    }
}
