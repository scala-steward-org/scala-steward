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

package org.scalasteward.core.repoconfig

import cats.MonadThrow
import cats.syntax.all.*
import org.http4s.Uri
import org.scalasteward.core.application.Config.RepoConfigCfg
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigLoader.defaultRepoConfigUrl
import org.typelevel.log4cats.Logger

final class RepoConfigLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def loadGlobalRepoConfig(config: RepoConfigCfg): F[Option[RepoConfig]] = {
    val maybeDefaultRepoConfigUrl =
      Option.unless(config.disableDefault)(defaultRepoConfigUrl)
    (maybeDefaultRepoConfigUrl.toList ++ config.repoConfigs)
      .traverse(loadRepoConfig)
      .flatTap(repoConfigs => logger.info(s"Loaded ${repoConfigs.size} repo config(s)"))
      .map(_.combineAllOption)
      .flatTap(
        _.fold(F.unit)(config => logger.debug(s"Effective global repo config: ${config.show}"))
      )
  }

  private def loadRepoConfig(uri: Uri): F[RepoConfig] =
    logger.debug(s"Loading repo config from $uri") >>
      fileAlg.readUri(uri).flatMap(decodeRepoConfig(_, uri))

  private def decodeRepoConfig(content: String, uri: Uri): F[RepoConfig] =
    F.fromEither(RepoConfigAlg.parseRepoConfig(content))
      .adaptErr(new Throwable(s"Failed to load repo config from ${uri.renderString}", _))
}

object RepoConfigLoader {
  val defaultRepoConfigUrl: Uri = Uri.unsafeFromString(
    s"${org.scalasteward.core.BuildInfo.gitHubUserContent}/modules/core/src/main/resources/default.scala-steward.conf"
  )
}
