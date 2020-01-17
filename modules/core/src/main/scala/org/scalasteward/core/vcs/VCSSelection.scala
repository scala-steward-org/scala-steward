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

import org.scalasteward.core.application.SupportedVCS.{Bitbucket, BitbucketServer, GitHub, Gitlab}
import org.scalasteward.core.bitbucket.http4s.Http4sBitbucketApiAlg
import org.scalasteward.core.bitbucketserver.http4s.Http4sBitbucketServerApiAlg
import org.scalasteward.core.github.http4s.Http4sGitHubApiAlg
import org.scalasteward.core.gitlab.http4s.Http4sGitLabApiAlg
import org.scalasteward.core.application.SupportedVCS

class VCSSelection[F[_]](
    implicit
    githubApi: Http4sGitHubApiAlg[F],
    gitlabApi: Http4sGitLabApiAlg[F],
    bitbucketApi: Http4sBitbucketApiAlg[F],
    bitbucketServerApi: Http4sBitbucketServerApiAlg[F]
) {

  def getAlg(vcsType: SupportedVCS): VCSApiAlg[F] = vcsType match {
    case GitHub          => githubApi
    case Gitlab          => gitlabApi
    case Bitbucket       => bitbucketApi
    case BitbucketServer => bitbucketServerApi
  }
}
