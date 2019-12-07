/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core

import cats.implicits._
import org.scalasteward.core.application.SupportedVCS
import org.scalasteward.core.application.SupportedVCS.{Bitbucket, BitbucketServer, GitHub, Gitlab}
import org.scalasteward.core.data.Update
import org.scalasteward.core.vcs.data.Repo

package object vcs {

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests.
    */
  def listingBranch(vcsType: SupportedVCS, fork: Repo, update: Update): String =
    vcsType match {
      case GitHub =>
        s"${fork.show}:${git.branchFor(update).name}"

      case Gitlab | Bitbucket | BitbucketServer =>
        git.branchFor(update).name
    }

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for creating
    * a new pull requests.
    */
  def createBranch(vcsType: SupportedVCS, fork: Repo, update: Update): String =
    vcsType match {
      case GitHub =>
        s"${fork.owner}:${git.branchFor(update).name}"

      case Gitlab | Bitbucket | BitbucketServer =>
        git.branchFor(update).name
    }

  def possibleTags(version: String): List[String] =
    List(s"v$version", version, s"release-$version")

  val possibleChangelogFilenames: List[String] = {
    val basenames = List(
      "CHANGELOG",
      "Changelog",
      "changelog",
      "CHANGES",
      "ReleaseNotes",
      "RELEASES",
      "Releases",
      "releases"
    )
    val extensions = List("md", "markdown", "rst")
    (basenames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }

  def possibleCompareUrls(repoUrl: String, update: Update): List[String] = {
    val from = update.currentVersion
    val to = update.nextVersion
    val canonicalized = repoUrl.replaceAll("/$", "")
    if (repoUrl.startsWith("https://github.com/") || repoUrl.startsWith("https://gitlab.com/"))
      possibleTags(from).zip(possibleTags(to)).map {
        case (from1, to1) => s"${canonicalized}/compare/$from1...$to1"
      }
    else if (repoUrl.startsWith("https://bitbucket.org/"))
      possibleTags(from).zip(possibleTags(to)).map {
        case (from1, to1) => s"${canonicalized}/compare/${to1}..${from1}#diff"
      }
    else
      List.empty
  }

  def possibleChangelogUrls(repoUrl: String, update: Update): List[String] = {
    val canonicalized = repoUrl.replaceAll("/$", "")
    val vcsSpecific =
      if (repoUrl.startsWith("https://github.com/"))
        possibleTags(update.nextVersion).map(tag => s"${canonicalized}/releases/tag/$tag")
      else
        List.empty
    val files = {
      val pathToFile =
        if (repoUrl.startsWith("https://github.com/") || repoUrl.startsWith("https://gitlab.com/")) {
          Some("blob/master")
        } else if (repoUrl.startsWith("https://bitbucket.org/")) {
          Some("master")
        } else {
          None
        }
      pathToFile.toList.flatMap { path =>
        possibleChangelogFilenames.map(name => s"${canonicalized}/${path}/$name")
      }
    }
    files ++ vcsSpecific
  }
}
