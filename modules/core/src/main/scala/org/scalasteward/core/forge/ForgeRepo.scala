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

import org.http4s.Uri

/** ForgeRepo encapsulates two concepts that are commonly considered together - the URI of a repo,
  * and the 'type' of forge that url represents. Given a URI, once we know it's a GitHub or GitLab
  * forge, etc, then we can know how to construct many of the urls for common resources existing at
  * that repo host- for instance, the url to view a particular file, or to diff two commits.
  */
trait ForgeRepo {

  /** Defines how to construct 'diff' urls for this forge type - ie a url that will show the
    * difference between two git tags. These can be very useful for understanding the difference
    * between two releases of the same artifact.
    */
  def diffUrlFor(from: String, to: String): Uri

  /** Defines how to construct 'file' urls for this forge type - ie a url that will display a
    * specific file's contents. This is useful for linking to Release Notes, etc, in a Scala Steward
    * PR description.
    */
  def fileUrlFor(fileName: String): Uri
}

object ForgeRepo {
  case class AzureRepos(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri =
      repoUrl / "branchCompare" +? ("baseVersion", s"GT$from") +? ("targetVersion", s"GT$to")
    override def fileUrlFor(fileName: String): Uri = repoUrl.withQueryParam(
      "path",
      fileName
    ) // Azure's canonical value for the path is prefixed with a slash?
  }

  case class Bitbucket(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri =
      (repoUrl / "compare" / s"$to..$from").withFragment("diff")
    override def fileUrlFor(fileName: String): Uri = repoUrl / "src" / "master" / fileName
  }

  case class BitbucketServer(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri =
      (repoUrl / "compare" / s"$to..$from").withFragment("diff")
    override def fileUrlFor(fileName: String): Uri = repoUrl / "browse" / fileName
  }

  case class GitHub(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri = repoUrl / "compare" / s"$from...$to"
    override def fileUrlFor(fileName: String): Uri = repoUrl / "blob" / "master" / fileName
  }

  case class GitLab(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri = repoUrl / "compare" / s"$from...$to"
    override def fileUrlFor(fileName: String): Uri = repoUrl / "blob" / "master" / fileName
  }

  case class Gitea(repoUrl: Uri) extends ForgeRepo {
    override def diffUrlFor(from: String, to: String): Uri = repoUrl / "compare" / s"$from...$to"
    override def fileUrlFor(fileName: String): Uri =
      repoUrl / "src" / "branch" / "master" / fileName
  }

  def fromRepoUrl(repoUrl: Uri, forge: Forge): Option[ForgeRepo] =
    repoUrl.host.flatMap { repoHost =>
      Option
        .when(forge.apiUri.host.contains(repoHost))(forge match {
          case _: Forge.AzureRepos      => AzureRepos(repoUrl)
          case _: Forge.Bitbucket       => Bitbucket(repoUrl)
          case _: Forge.BitbucketServer => BitbucketServer(repoUrl)
          case _: Forge.GitHub          => GitHub(repoUrl)
          case _: Forge.GitLab          => GitLab(repoUrl)
          case _: Forge.Gitea           => Gitea(repoUrl)
        })
        .orElse(repoHost.value match {
          case "dev.azure.com"     => Some(AzureRepos(repoUrl))
          case "api.bitbucket.org" => Some(Bitbucket(repoUrl))
          case "api.github.com"    => Some(GitHub(repoUrl))
          case "gitlab.com"        => Some(GitLab(repoUrl))
          case _                   => None
        })
    }
}
