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

package org.scalasteward.core.coursier

import cats.Monad
import cats.syntax.all.*
import org.http4s.Uri
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.forge.ForgeRepo
import org.scalasteward.core.util.uri

final case class DependencyMetadata(
    homePage: Option[Uri],
    scmUrl: Option[Uri],
    releaseNotesUrl: Option[Uri],
    versionScheme: Option[String] = None
) {
  def enrichWith(other: DependencyMetadata): DependencyMetadata =
    DependencyMetadata(
      homePage = homePage.orElse(other.homePage),
      scmUrl = scmUrl.orElse(other.scmUrl),
      releaseNotesUrl = releaseNotesUrl.orElse(other.releaseNotesUrl),
      versionScheme = versionScheme.orElse(other.versionScheme)
    )

  def filterUrls[F[_]](f: Uri => F[Boolean])(implicit F: Monad[F]): F[DependencyMetadata] =
    for {
      homePage <- homePage.filterA(f)
      scmUrl <- scmUrl.filterA(f)
      releaseNotesUrl <- releaseNotesUrl.filterA(f)
    } yield DependencyMetadata(homePage, scmUrl, releaseNotesUrl, versionScheme)

  def repoUrl: Option[Uri] = {
    val urls = scmUrl.toList ++ homePage.toList
    urls.find(_.scheme.exists(uri.httpSchemes)).orElse(urls.headOption)
  }

  def forgeRepo(implicit config: ForgeCfg): Option[ForgeRepo] =
    repoUrl.flatMap(ForgeRepo.fromRepoUrl)
}

object DependencyMetadata {
  val empty: DependencyMetadata =
    DependencyMetadata(None, None, None, None)
}
