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

final class VCSSelection[F[_]](implicit
    client: HttpJsonClient[F],
    user: AuthenticatedUser,
    logger: Logger[F],
    F: MonadThrow[F]
) {
  private def github(config: Config): GitHubApiAlg[F] = {
    import org.scalasteward.core.vcs.github.authentication.addCredentials

    new GitHubApiAlg[F](config.vcsApiHost, _ => addCredentials(user))
  }

  private def gitlab(config: Config): GitLabApiAlg[F] = {
    import org.scalasteward.core.vcs.gitlab.authentication.addCredentials
    new GitLabApiAlg[F](
      config.vcsApiHost,
      user,
      _ => addCredentials(user),
      config.doNotFork,
      config.gitlabMergeWhenPipelineSucceeds
    )
  }

  private def bitbucket(config: Config): BitbucketApiAlg[F] = {
    import org.scalasteward.core.vcs.bitbucket.authentication.addCredentials

    new BitbucketApiAlg(config.vcsApiHost, user, _ => addCredentials(user), config.doNotFork)
  }

  private def bitbucketServer(config: Config): BitbucketServerApiAlg[F] = {
    import org.scalasteward.core.vcs.bitbucket.authentication.addCredentials

    // Bypass the server-side XSRF check, see
    // https://github.com/scala-steward-org/scala-steward/pull/1863#issuecomment-754538364
    val xAtlassianToken = Header("X-Atlassian-Token", "no-check")

    new BitbucketServerApiAlg[F](
      config.vcsApiHost,
      _ => req => addCredentials(user).apply(req.putHeaders(xAtlassianToken)),
      config.bitbucketServerUseDefaultReviewers
    )
  }

  def getAlg(config: Config): VCSApiAlg[F] =
    config.vcsType match {
      case GitHub          => github(config)
      case GitLab          => gitlab(config)
      case Bitbucket       => bitbucket(config)
      case BitbucketServer => bitbucketServer(config)
    }
}
