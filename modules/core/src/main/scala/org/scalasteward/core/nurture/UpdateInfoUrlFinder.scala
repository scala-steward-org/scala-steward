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

final class UpdateInfoUrlFinder[F[_]](implicit
    config: ForgeCfg,
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
          ForgeType.fromRepoUrl(repoUrl).toSeq.flatMap { forgeType =>
            possibleUpdateInfoUrls(forgeType, repoUrl, currentVersion, nextVersion)
          }
        }

    updateInfoUrls
      .sorted(UpdateInfoUrl.updateInfoUrlOrder.toOrdering)
      .distinctBy(_.url)
      .filterA(updateInfoUrl => urlChecker.exists(updateInfoUrl.url))
  }
}

object UpdateInfoUrlFinder {

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

  private[nurture] def possibleVersionDiffs(
      forgeType: ForgeType,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[VersionDiff] = for {
    tagName <- Version.tagNames
  } yield VersionDiff(
    forgeType.diffs.forDiff(tagName(currentVersion), tagName(nextVersion))(repoUrl)
  )

  private[nurture] def possibleUpdateInfoUrls(
      forgeType: ForgeType,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[UpdateInfoUrl] = {
    def customUrls(wrap: Uri => UpdateInfoUrl, fileNames: List[String]): List[UpdateInfoUrl] =
      fileNames.map(f => wrap(forgeType.files.forFile(f)(repoUrl)))

    gitHubReleaseNotesFor(forgeType, repoUrl, nextVersion) ++
      customUrls(CustomReleaseNotes, possibleReleaseNotesFilenames) ++
      customUrls(CustomChangelog, possibleChangelogFilenames) ++
      possibleVersionDiffs(forgeType, repoUrl, currentVersion, nextVersion)
  }

  private def gitHubReleaseNotesFor(
      forgeType: ForgeType,
      repoUrl: Uri,
      version: Version
  ): List[UpdateInfoUrl] =
    forgeType match {
      case GitHub =>
        Version.tagNames
          .map(tagName => GitHubReleaseNotes(repoUrl / "releases" / "tag" / tagName(version)))
      case _ => Nil
    }

  private def possibleFilenames(baseNames: List[String]): List[String] = {
    val extensions = List("md", "markdown", "rst")
    (baseNames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }
}
