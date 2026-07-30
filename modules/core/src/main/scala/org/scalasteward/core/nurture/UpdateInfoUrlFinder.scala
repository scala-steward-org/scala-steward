/*
 * Copyright 2018-2025 Scala Steward contributors
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

import cats.{Functor, Monad, Parallel}
import cats.syntax.all.*
import org.http4s.Uri
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.Version
import org.scalasteward.core.forge.ForgeRepo
import org.scalasteward.core.forge.ForgeType.*
import org.scalasteward.core.nurture.UpdateInfoUrl.*
import org.scalasteward.core.nurture.UpdateInfoUrlFinder.possibleUpdateInfoUrls
import org.scalasteward.core.util.UrlChecker

final class UpdateInfoUrlFinder[F[_]](implicit
    config: ForgeCfg,
    urlChecker: UrlChecker[F],
    parallel: Parallel[F],
    F: Monad[F]
) {
  def findUpdateInfoUrls(
      dependency: DependencyMetadata,
      versionUpdate: Version.Update
  ): F[List[UpdateInfoUrl]] = {
    val updateInfoUrls: F[List[UpdateInfoUrl]] =
      dependency.forgeRepo.toList
        .flatTraverse(forgeRepo => possibleUpdateInfoUrls(urlChecker)(forgeRepo, versionUpdate))
        .map(dependency.releaseNotesUrl.toList.map(CustomReleaseNotes.apply) ++ _)

    for {
      urls <- updateInfoUrls
        .map(
          _.sorted(UpdateInfoUrl.updateInfoUrlOrder.toOrdering)
            .distinctBy(_.url)
        )
      checkedUrls <-
        urls.parFlatTraverse(updateInfoUrl =>
          urlChecker
            .validate(updateInfoUrl.url)
            .map(
              _.fold(
                exists = List(updateInfoUrl),
                notExists = List.empty,
                redirectTo = u => List(updateInfoUrl.withUrl(u))
              )
            )
        )
    } yield checkedUrls
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
      repoForge: ForgeRepo,
      update: Version.Update
  ): List[VersionDiff] = for {
    tagName <- Version.tagNames
  } yield VersionDiff(
    repoForge.diffUrlFor(tagName(update.currentVersion), tagName(update.nextVersion))
  )

  private[nurture] def possibleUpdateInfoUrls[F[_]: Functor](urlChecker: UrlChecker[F])(
      forgeRepo: ForgeRepo,
      update: Version.Update
  ): F[List[UpdateInfoUrl]] = {
    def customUrls(wrap: Uri => UpdateInfoUrl, fileNames: List[String]): List[UpdateInfoUrl] =
      fileNames.map(f => wrap(forgeRepo.fileUrlFor(f)))

    for {
      forgeRepoMaybe <- urlChecker
        .validate(forgeRepo.repoUrl)
        .map(
          _.fold(
            exists = forgeRepo.some,
            notExists = none,
            redirectTo = u => forgeRepo.copy(repoUrl = u).some
          )
        )
      urls =
        forgeRepoMaybe.toList.flatMap(forgeRepoChecked =>
          gitHubReleaseNotesFor(forgeRepoChecked, update.nextVersion) ++
            customUrls(CustomReleaseNotes.apply, possibleReleaseNotesFilenames) ++
            customUrls(CustomChangelog.apply, possibleChangelogFilenames) ++
            possibleVersionDiffs(forgeRepoChecked, update)
        )
    } yield urls
  }

  private def gitHubReleaseNotesFor(
      forgeRepo: ForgeRepo,
      version: Version
  ): List[UpdateInfoUrl] =
    forgeRepo.forgeType match {
      case GitHub =>
        Version.tagNames
          .map(tagName =>
            GitHubReleaseNotes(forgeRepo.repoUrl / "releases" / "tag" / tagName(version))
          )
      case _ => Nil
    }

  private def possibleFilenames(baseNames: List[String]): List[String] = {
    val extensions = List("md", "markdown", "rst")
    (baseNames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }
}
