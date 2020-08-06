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

package org.scalasteward.core

import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.application.SupportedVCS
import org.scalasteward.core.application.SupportedVCS.{Bitbucket, BitbucketServer, GitHub, Gitlab}
import org.scalasteward.core.data.ReleaseRelatedUrl.VersionDiff
import org.scalasteward.core.data.{ReleaseRelatedUrl, Update}
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
    val baseNames = List(
      "CHANGELOG",
      "Changelog",
      "changelog",
      "CHANGES"
    )
    possibleFilenames(baseNames)
  }

  val possibleReleaseNotesFilenames: List[String] = {
    val baseNames = List(
      "ReleaseNotes",
      "RELEASES",
      "Releases",
      "releases"
    )
    possibleFilenames(baseNames)
  }

  def possibleCompareUrls(
      vcsType: SupportedVCS,
      repoUrl: Uri,
      update: Update
  ): List[VersionDiff] = {
    val from = update.currentVersion
    val to = update.nextVersion

    vcsType match {
      case GitHub | Gitlab =>
        possibleTags(from).zip(possibleTags(to)).map {
          case (from1, to1) => VersionDiff(repoUrl / "compare" / s"$from1...$to1")
        }
      case Bitbucket | BitbucketServer =>
        possibleTags(from).zip(possibleTags(to)).map {
          case (from1, to1) =>
            VersionDiff((repoUrl / "compare" / s"$to1..$from1").withFragment("diff"))
        }
      case _ => List.empty
    }
  }

  def possibleReleaseRelatedUrls(
      vcsType: SupportedVCS,
      repoUrl: Uri,
      update: Update
  ): List[ReleaseRelatedUrl] = {
    val github = vcsType match {
      case GitHub =>
        possibleTags(update.nextVersion).map(tag =>
          ReleaseRelatedUrl.GitHubReleaseNotes(repoUrl / "releases" / "tag" / tag)
        )
      case _ => List.empty
    }

    def files(fileNames: List[String]): List[Uri] = {
      val maybeSegments =
        vcsType match {
          case SupportedVCS.GitHub | SupportedVCS.Gitlab             => Some(List("blob", "master"))
          case SupportedVCS.Bitbucket | SupportedVCS.BitbucketServer => Some(List("master"))
          case _                                                     => None
        }

      maybeSegments.toList.flatMap { segments =>
        val base = segments.foldLeft(repoUrl)(_ / _)
        fileNames.map(name => base / name)
      }
    }
    val customChangelog = files(possibleChangelogFilenames).map(ReleaseRelatedUrl.CustomChangelog)
    val customReleaseNotes =
      files(possibleReleaseNotesFilenames).map(ReleaseRelatedUrl.CustomReleaseNotes)

    github ++ customReleaseNotes ++ customChangelog ++ possibleCompareUrls(vcsType, repoUrl, update)
  }

  private def possibleFilenames(baseNames: List[String]): List[String] = {
    val extensions = List("md", "markdown", "rst")
    (baseNames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }
}
