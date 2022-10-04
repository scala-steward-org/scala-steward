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

package org.scalasteward.core.nurture

import cats.Monad
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.Version
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.nurture.UpdateInfoUrl._
import org.scalasteward.core.nurture.UpdateInfoUrlFinder.possibleUpdateInfoUrls
import org.scalasteward.core.util.UrlChecker

final class UpdateInfoUrlFinder[F[_]](config: ForgeCfg)(implicit
    urlChecker: UrlChecker[F],
    F: Monad[F]
) {
  def findUpdateInfoUrls(
      metadata: DependencyMetadata,
      currentVersion: Version,
      nextVersion: Version
  ): F[List[UpdateInfoUrl]] = {
    val updateInfoUrls =
      metadata.releaseNotesUrl.toList.map(CustomReleaseNotes.apply) ++
        metadata.repoUrl.toList.flatMap { repoUrl =>
          possibleUpdateInfoUrls(config.tpe, config.apiHost, repoUrl, currentVersion, nextVersion)
        }

    updateInfoUrls
      .sorted(UpdateInfoUrl.updateInfoUrlOrder.toOrdering)
      .distinctBy(_.url)
      .filterA(updateInfoUrl => urlChecker.exists(updateInfoUrl.url))
  }
}

object UpdateInfoUrlFinder {
  private def possibleTags(version: Version): List[String] =
    List(s"v$version", version.value, s"release-$version")

  private[nurture] val possibleChangelogFilenames: List[String] = {
    val baseNames = List(
      "CHANGELOG",
      "Changelog",
      "changelog",
      "CHANGES"
    )
    possibleFilenames(baseNames)
  }

  private[nurture] val possibleReleaseNotesFilenames: List[String] = {
    val baseNames = List(
      "ReleaseNotes",
      "RELEASES",
      "Releases",
      "releases"
    )
    possibleFilenames(baseNames)
  }

  private def forgeTypeFromRepoUrl(
      forgeType: ForgeType,
      forgeUrl: Uri,
      repoUrl: Uri
  ): Option[ForgeType] =
    repoUrl.host.flatMap { repoHost =>
      if (forgeUrl.host.contains(repoHost)) Some(forgeType)
      else ForgeType.fromPublicWebHost(repoHost.value)
    }

  private[nurture] def possibleVersionDiffs(
      forgeType: ForgeType,
      forgeUrl: Uri,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[VersionDiff] =
    forgeTypeFromRepoUrl(forgeType, forgeUrl, repoUrl).map {
      case GitHub | GitLab | Gitea =>
        possibleTags(currentVersion).zip(possibleTags(nextVersion)).map { case (from1, to1) =>
          VersionDiff(repoUrl / "compare" / s"$from1...$to1")
        }
      case Bitbucket | BitbucketServer =>
        possibleTags(currentVersion).zip(possibleTags(nextVersion)).map { case (from1, to1) =>
          VersionDiff((repoUrl / "compare" / s"$to1..$from1").withFragment("diff"))
        }
      case AzureRepos =>
        possibleTags(currentVersion).zip(possibleTags(nextVersion)).map { case (from1, to1) =>
          VersionDiff(
            (repoUrl / "branchCompare")
              .withQueryParams(Map("baseVersion" -> from1, "targetVersion" -> to1))
          )
        }
    }.orEmpty

  private[nurture] def possibleUpdateInfoUrls(
      forgeType: ForgeType,
      forgeUrl: Uri,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[UpdateInfoUrl] = {
    val repoForgeType = forgeTypeFromRepoUrl(forgeType, forgeUrl, repoUrl)

    val githubReleaseNotes = repoForgeType
      .collect { case GitHub =>
        possibleTags(nextVersion).map(tag => GitHubReleaseNotes(repoUrl / "releases" / "tag" / tag))
      }
      .getOrElse(List.empty)

    def files(fileNames: List[String]): List[Uri] = {
      val maybeSegments = repoForgeType.collect {
        case GitHub | GitLab => List("blob", "master")
        case Bitbucket       => List("master")
        case BitbucketServer => List("browse")
        case Gitea           => List("src", "branch", "master")
      }

      val repoFiles = maybeSegments.toList.flatMap { segments =>
        val base = segments.foldLeft(repoUrl)(_ / _)
        fileNames.map(name => base / name)
      }

      val azureRepoFiles = repoForgeType
        .collect { case AzureRepos => fileNames.map(name => repoUrl.withQueryParam("path", name)) }
        .toList
        .flatten

      repoFiles ++ azureRepoFiles
    }

    val customChangelog = files(possibleChangelogFilenames).map(CustomChangelog)
    val customReleaseNotes = files(possibleReleaseNotesFilenames).map(CustomReleaseNotes)

    githubReleaseNotes ++ customReleaseNotes ++ customChangelog ++
      possibleVersionDiffs(forgeType, forgeUrl, repoUrl, currentVersion, nextVersion)
  }

  private def possibleFilenames(baseNames: List[String]): List[String] = {
    val extensions = List("md", "markdown", "rst")
    (baseNames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }
}
