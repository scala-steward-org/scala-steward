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

import better.files.File
import cats.Eq
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import scala.annotation.nowarn

sealed trait Forge extends Product with Serializable {
  def apiUri: Uri
  def doNotFork: Boolean
  def addLabels: Boolean

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests or creating new pull requests.
    */
  def pullRequestHeadFor(@nowarn fork: Repo, updateBranch: Branch): String = updateBranch.name
}

object Forge {
  case class AzureRepos(
      apiUri: Uri,
      login: String,
      gitAskPass: File,
      addLabels: Boolean,
      reposOrganization: String
  ) extends Forge {

    /** Azure Repos does not support forking */
    val doNotFork: Boolean = true
    override val toString: String = "azure-repos"
  }

  case class Bitbucket(
      apiUri: Uri,
      login: String,
      gitAskPass: File,
      doNotFork: Boolean,
      useDefaultReviewers: Boolean
  ) extends Forge {

    /** Bitbucket does not support labels on PRs */
    val addLabels: Boolean = false
    override val toString: String = "bitbucket"
  }
  object Bitbucket {
    val defaultApiUri: Uri = uri"https://api.bitbucket.org/2.0"
  }

  /** Note Bitbucket Server will be End Of Service Life on 15th February 2024:
    *
    * https://www.atlassian.com/software/bitbucket/enterprise
    * https://www.atlassian.com/migration/assess/journey-to-cloud
    */
  case class BitbucketServer(
      apiUri: Uri,
      login: String,
      gitAskPass: File,
      useDefaultReviewers: Boolean
  ) extends Forge {

    /** Bitbucket Server does not support forking */
    val doNotFork: Boolean = true

    /** Bitbucket Server does not support labels on PRs */
    val addLabels: Boolean = false
    override val toString: String = "bitbucket-server"
  }

  case class GitHub(
      apiUri: Uri,
      doNotFork: Boolean,
      addLabels: Boolean,
      appId: Long,
      appKeyFile: File
  ) extends Forge {
    override def pullRequestHeadFor(fork: Repo, updateBranch: Branch): String =
      s"${fork.owner}:${updateBranch.name}"
    override val toString: String = "github"
  }
  object GitHub {
    val defaultApiUri: Uri = uri"https://api.github.com"
  }

  case class GitLab(
      apiUri: Uri,
      login: String,
      gitAskPass: File,
      doNotFork: Boolean,
      addLabels: Boolean,
      mergeWhenPipelineSucceeds: Boolean,
      requiredReviewers: Option[Int],
      removeSourceBranch: Boolean
  ) extends Forge
  object GitLab {
    val defaultApiUri: Uri = uri"https://gitlab.com/api/v4"
    override val toString: String = "gitlab"
  }

  case class Gitea(
      apiUri: Uri,
      login: String,
      gitAskPass: File,
      doNotFork: Boolean,
      addLabels: Boolean
  ) extends Forge {
    override val toString: String = "gitea"
  }

  implicit val forgeEq: Eq[Forge] = Eq.fromUniversalEquals
}
