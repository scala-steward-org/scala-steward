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

import cats.effect.Temporal
import cats.syntax.all._
import cats.{Applicative, Functor, Parallel}
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Header, Request}
import org.scalasteward.core.application.Config
import org.scalasteward.core.application.Config.{ForgeCfg, ForgeSpecificCfg}
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.forge.azurerepos.AzureReposApiAlg
import org.scalasteward.core.forge.bitbucket.BitbucketApiAlg
import org.scalasteward.core.forge.bitbucketserver.BitbucketServerApiAlg
import org.scalasteward.core.forge.data.AuthenticatedUser
import org.scalasteward.core.forge.gitea.GiteaApiAlg
import org.scalasteward.core.forge.github.GitHubApiAlg
import org.scalasteward.core.forge.gitlab.GitLabApiAlg
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.ci._
import org.typelevel.log4cats.Logger

object ForgeSelection {
  def forgeApiAlg[F[_]: Parallel](
      forgeCfg: ForgeCfg,
      forgeSpecificCfg: ForgeSpecificCfg,
      user: F[AuthenticatedUser]
  )(implicit
      httpJsonClient: HttpJsonClient[F],
      logger: Logger[F],
      F: Temporal[F]
  ): ForgeApiAlg[F] = {
    val auth = authenticate(forgeCfg.tpe, user)
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

  def authenticate[F[_]](
      forgeType: ForgeType,
      user: F[AuthenticatedUser]
  )(implicit F: Functor[F]): Request[F] => F[Request[F]] =
    forgeType match {
      case AzureRepos      => req => user.map(u => req.putHeaders(basicAuth(u)))
      case Bitbucket       => req => user.map(u => req.putHeaders(basicAuth(u)))
      case BitbucketServer => req => user.map(u => req.putHeaders(basicAuth(u), xAtlassianToken))
      case GitHub          => req => user.map(u => req.putHeaders(basicAuth(u)))
      case GitLab          => req => user.map(u => req.putHeaders(privateToken(u)))
      case Gitea           => req => user.map(u => req.putHeaders(basicAuth(u)))
    }

  private def basicAuth(user: AuthenticatedUser): Authorization =
    Authorization(BasicCredentials(user.login, user.accessToken))

  private def privateToken(user: AuthenticatedUser): Header.Raw =
    Header.Raw(ci"Private-Token", user.accessToken)

  // Bypass the server-side XSRF check, see
  // https://github.com/scala-steward-org/scala-steward/pull/1863#issuecomment-754538364
  private val xAtlassianToken = Header.Raw(ci"X-Atlassian-Token", "no-check")

  def authenticateIfApiHost[F[_]](
      forgeCfg: ForgeCfg,
      user: F[AuthenticatedUser]
  )(implicit F: Applicative[F]): Request[F] => F[Request[F]] =
    req => {
      val sameScheme = req.uri.scheme === forgeCfg.apiHost.scheme
      val sameHost = req.uri.host === forgeCfg.apiHost.host
      if (sameScheme && sameHost) authenticate(forgeCfg.tpe, user)(F)(req)
      else req.pure[F]
    }
}
