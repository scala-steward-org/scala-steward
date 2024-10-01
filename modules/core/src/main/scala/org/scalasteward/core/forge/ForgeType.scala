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

import cats.Eq
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.unexpectedString

import scala.annotation.nowarn

sealed trait ForgeType extends Product with Serializable {
  def publicWebHost: Option[String]

  /** Defines how to construct 'diff' urls for this forge type - ie a url that will show the
    * difference between two git tags. These can be very useful for understanding the difference
    * between two releases of the same artifact.
    */
  val diffs: DiffUriPattern

  /** Defines how to construct 'file' urls for this forge type - ie a url that will display a
    * specific file's contents. This is useful for linking to Release Notes, etc, in a Scala Steward
    * PR description.
    */
  val files: FileUriPattern
  def supportsForking: Boolean = true
  def supportsLabels: Boolean = true

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests or creating new pull requests.
    */
  def pullRequestHeadFor(@nowarn fork: Repo, updateBranch: Branch): String = updateBranch.name

  val asString: String = this match {
    case AzureRepos      => "azure-repos"
    case Bitbucket       => "bitbucket"
    case BitbucketServer => "bitbucket-server"
    case GitHub          => "github"
    case GitLab          => "gitlab"
    case Gitea           => "gitea"
  }
}

object ForgeType {
  trait DiffUriPattern { def forDiff(from: String, to: String): Uri => Uri }
  trait FileUriPattern { def forFile(fileName: String): Uri => Uri }

  case object AzureRepos extends ForgeType {
    override val publicWebHost: Some[String] = Some("dev.azure.com")
    override def supportsForking: Boolean = false
    val diffs: DiffUriPattern = (from, to) =>
      _ / "branchCompare" +? ("baseVersion", s"GT$from") +? ("targetVersion", s"GT$to")
    val files: FileUriPattern =
      fileName =>
        _.withQueryParam(
          "path",
          fileName
        ) // Azure's canonical value for the path is prefixed with a slash?
  }

  case object Bitbucket extends ForgeType {
    override val publicWebHost: Some[String] = Some("bitbucket.org")
    override def supportsLabels: Boolean = false
    val publicApiBaseUrl = uri"https://api.bitbucket.org/2.0"
    val diffs: DiffUriPattern = (from, to) => _ / "compare" / s"$to..$from" withFragment "diff"
    val files: FileUriPattern = fileName => _ / "src" / "master" / fileName
  }

  /** Note Bitbucket Server will be End Of Service Life on 15th February 2024:
    *
    * https://www.atlassian.com/software/bitbucket/enterprise
    * https://www.atlassian.com/migration/assess/journey-to-cloud
    */
  case object BitbucketServer extends ForgeType {
    override val publicWebHost: None.type = None
    override def supportsForking: Boolean = false
    override def supportsLabels: Boolean = false
    val diffs: DiffUriPattern = Bitbucket.diffs
    val files: FileUriPattern = fileName => _ / "browse" / fileName
  }

  case object GitHub extends ForgeType {
    override val publicWebHost: Some[String] = Some("github.com")
    val publicApiBaseUrl = uri"https://api.github.com"
    val diffs: DiffUriPattern = (from, to) => _ / "compare" / s"$from...$to"
    val files: FileUriPattern = fileName => _ / "blob" / "master" / fileName
    override def pullRequestHeadFor(fork: Repo, updateBranch: Branch): String =
      s"${fork.owner}:${updateBranch.name}"
  }

  case object GitLab extends ForgeType {
    override val publicWebHost: Some[String] = Some("gitlab.com")
    val publicApiBaseUrl = uri"https://gitlab.com/api/v4"
    val diffs: DiffUriPattern = GitHub.diffs
    val files: FileUriPattern = GitHub.files
  }

  case object Gitea extends ForgeType {
    override val publicWebHost: Option[String] = None
    val diffs: DiffUriPattern = GitHub.diffs
    val files: FileUriPattern = fileName => _ / "src" / "branch" / "master" / fileName
  }

  val all: List[ForgeType] = List(AzureRepos, Bitbucket, BitbucketServer, GitHub, GitLab, Gitea)

  def allNot(f: ForgeType => Boolean): String =
    ForgeType.all.filterNot(f).map(_.asString).mkString(", ")

  def parse(s: String): Either[String, ForgeType] =
    all.find(_.asString === s) match {
      case Some(value) => Right(value)
      case None        => unexpectedString(s, all.map(_.asString))
    }

  def fromPublicWebHost(host: String): Option[ForgeType] =
    all.find(_.publicWebHost.contains_(host))

  /** Attempts to guess, based on the uri host and the config used to launch Scala Steward, what
    * type of forge hosts the repo at the supplied uri.
    */
  def fromRepoUrl(repoUrl: Uri)(implicit config: ForgeCfg): Option[ForgeType] =
    repoUrl.host.flatMap { repoHost =>
      Option
        .when(config.apiHost.host.contains(repoHost))(config.tpe)
        .orElse(fromPublicWebHost(repoHost.value))
    }

  implicit val forgeTypeEq: Eq[ForgeType] =
    Eq.fromUniversalEquals
}
