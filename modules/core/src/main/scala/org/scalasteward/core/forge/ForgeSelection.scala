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

import cats.syntax.all._
import cats.{Applicative, MonadThrow}
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Header, Request}
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

object ForgeSelection {
  def forgeApiAlg[F[_]](config: Config, user: AuthenticatedUser)(implicit
      httpJsonClient: HttpJsonClient[F],
      logger: Logger[F],
      F: MonadThrow[F]
  ): ForgeApiAlg[F] = {
    val auth = (_: Any) => authenticate(config.forgeCfg.tpe, user)
    config.forgeCfg.tpe match {
      case AzureRepos =>
        new AzureReposApiAlg[F](config.forgeCfg.apiHost, config.azureReposConfig, auth)
      case Bitbucket =>
        new BitbucketApiAlg(config.forgeCfg, config.bitbucketCfg, auth)
      case BitbucketServer =>
        new BitbucketServerApiAlg[F](config.forgeCfg.apiHost, config.bitbucketServerCfg, auth)
      case GitHub =>
        new GitHubApiAlg[F](config.forgeCfg.apiHost, auth)
      case GitLab =>
        new GitLabApiAlg[F](config.forgeCfg, config.gitLabCfg, auth)
    }
  }

  def authenticate[F[_]](
      forgeType: ForgeType,
      user: AuthenticatedUser
  )(implicit F: Applicative[F]): Request[F] => F[Request[F]] =
    forgeType match {
      case AzureRepos      => _.putHeaders(basicAuth(user)).pure[F]
      case Bitbucket       => _.putHeaders(basicAuth(user)).pure[F]
      case BitbucketServer =>
        // Bypass the server-side XSRF check, see
        // https://github.com/scala-steward-org/scala-steward/pull/1863#issuecomment-754538364
        val xAtlassianToken = Header.Raw(ci"X-Atlassian-Token", "no-check")
        _.putHeaders(basicAuth(user), xAtlassianToken).pure[F]
      case GitHub => _.putHeaders(basicAuth(user)).pure[F]
      case GitLab => _.putHeaders(Header.Raw(ci"Private-Token", user.accessToken)).pure[F]
    }

  private def basicAuth(user: AuthenticatedUser): Authorization =
    Authorization(BasicCredentials(user.login, user.accessToken))
}
