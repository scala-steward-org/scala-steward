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

package org.scalasteward.core.forge

import cats.MonadThrow
import org.http4s.Header
import org.scalasteward.core.application.Config
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.forge.azurerepos.AzureReposApiAlg
import org.scalasteward.core.forge.bitbucket.BitbucketApiAlg
import org.scalasteward.core.forge.bitbucketserver.BitbucketServerApiAlg
import org.scalasteward.core.forge.data.AuthenticatedUser
import org.scalasteward.core.forge.github.GitHubApiAlg
import org.scalasteward.core.forge.gitlab.GitLabApiAlg
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.ci._
import org.typelevel.log4cats.Logger

final class ForgeSelection[F[_]](config: Config, user: AuthenticatedUser)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  private def gitHubApiAlg: GitHubApiAlg[F] =
    new GitHubApiAlg[F](
      config.forgeCfg.apiHost,
      _ => github.authentication.addCredentials(user)
    )

  private def gitLabApiAlg: GitLabApiAlg[F] =
    new GitLabApiAlg[F](
      config.forgeCfg,
      config.gitLabCfg,
      _ => gitlab.authentication.addCredentials(user)
    )

  private def bitbucketApiAlg: BitbucketApiAlg[F] =
    new BitbucketApiAlg(
      config.forgeCfg,
      config.bitbucketCfg,
      _ => bitbucket.authentication.addCredentials(user)
    )

  private def bitbucketServerApiAlg: BitbucketServerApiAlg[F] = {
    // Bypass the server-side XSRF check, see
    // https://github.com/scala-steward-org/scala-steward/pull/1863#issuecomment-754538364
    val xAtlassianToken = Header.Raw(ci"X-Atlassian-Token", "no-check")

    new BitbucketServerApiAlg[F](
      config.forgeCfg.apiHost,
      config.bitbucketServerCfg,
      _ =>
        req => bitbucket.authentication.addCredentials(user).apply(req.putHeaders(xAtlassianToken))
    )
  }

  private def azureReposApiAlg: AzureReposApiAlg[F] =
    new AzureReposApiAlg[F](
      config.forgeCfg.apiHost,
      config.azureReposConfig,
      _ => azurerepos.authentication.addCredentials(user)
    )

  def forgeApiAlg: ForgeApiAlg[F] =
    config.forgeCfg.tpe match {
      case GitHub          => gitHubApiAlg
      case GitLab          => gitLabApiAlg
      case Bitbucket       => bitbucketApiAlg
      case BitbucketServer => bitbucketServerApiAlg
      case AzureRepos      => azureReposApiAlg
    }
}
