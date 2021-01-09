/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.vcs

import cats.MonadThrow
import io.chrisdavenport.log4cats.Logger
import org.http4s.Header
import org.scalasteward.core.application.Config
import org.scalasteward.core.application.SupportedVCS.{Bitbucket, BitbucketServer, GitHub, GitLab}
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.bitbucket.BitbucketApiAlg
import org.scalasteward.core.vcs.bitbucketserver.BitbucketServerApiAlg
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.scalasteward.core.vcs.github.GitHubApiAlg
import org.scalasteward.core.vcs.gitlab.GitLabApiAlg

final class VCSSelection[F[_]](config: Config, user: AuthenticatedUser)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  private def gitHubApiAlg: GitHubApiAlg[F] =
    new GitHubApiAlg[F](config.vcsApiHost, _ => github.authentication.addCredentials(user))

  private def gitLabApiAlg: GitLabApiAlg[F] =
    new GitLabApiAlg[F](
      config.vcsApiHost,
      config.doNotFork,
      config.gitLabCfg,
      user,
      _ => gitlab.authentication.addCredentials(user)
    )

  private def bitbucketApiAlg: BitbucketApiAlg[F] =
    new BitbucketApiAlg(
      config.vcsApiHost,
      user,
      _ => bitbucket.authentication.addCredentials(user),
      config.doNotFork
    )

  private def bitbucketServerApiAlg: BitbucketServerApiAlg[F] = {
    // Bypass the server-side XSRF check, see
    // https://github.com/scala-steward-org/scala-steward/pull/1863#issuecomment-754538364
    val xAtlassianToken = Header("X-Atlassian-Token", "no-check")

    new BitbucketServerApiAlg[F](
      config.vcsApiHost,
      config.bitbucketServerCfg,
      _ =>
        req => bitbucket.authentication.addCredentials(user).apply(req.putHeaders(xAtlassianToken))
    )
  }

  def vcsApiAlg: VCSApiAlg[F] =
    config.vcsType match {
      case GitHub          => gitHubApiAlg
      case GitLab          => gitLabApiAlg
      case Bitbucket       => bitbucketApiAlg
      case BitbucketServer => bitbucketServerApiAlg
    }
}
