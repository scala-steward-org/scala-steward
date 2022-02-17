/*
 * Copyright 2018-2022 Scala Steward contributors
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

import cats.Monad
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.data.{ReleaseRelatedUrl, Update}
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.vcs

trait VCSExtraAlg[F[_]] {
  def getReleaseRelatedUrls(repoUrl: Uri, update: Update): F[List[ReleaseRelatedUrl]]
}

object VCSExtraAlg {
  def create[F[_]](config: VCSCfg)(implicit
      urlChecker: UrlChecker[F],
      F: Monad[F]
  ): VCSExtraAlg[F] =
    new VCSExtraAlg[F] {
      override def getReleaseRelatedUrls(repoUrl: Uri, update: Update): F[List[ReleaseRelatedUrl]] =
        vcs
          .possibleReleaseRelatedUrls(config.tpe, config.apiHost, repoUrl, update)
          .filterA(releaseRelatedUrl => urlChecker.exists(releaseRelatedUrl.url))
    }
}
