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

package org.scalasteward.core.update.artifact

import cats.MonadThrow
import cats.syntax.all.*
import io.circe.config.parser.decode
import org.http4s.Uri
import org.scalasteward.core.application.Config.ArtifactCfg
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.update.artifact.ArtifactMigrationsLoader.defaultArtifactMigrationsUrl
import org.typelevel.log4cats.Logger

final class ArtifactMigrationsLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def createFinder(config: ArtifactCfg): F[ArtifactMigrationsFinder] =
    loadAll(config).map(new ArtifactMigrationsFinder(_))

  def loadAll(config: ArtifactCfg): F[List[ArtifactChange]] = {
    val maybeDefaultMigrationsUrl =
      Option.unless(config.disableDefaults)(defaultArtifactMigrationsUrl)
    (maybeDefaultMigrationsUrl.toList ++ config.migrations)
      .flatTraverse(loadMigrations)
      .flatTap(migrations => logger.info(s"Loaded ${migrations.size} artifact migration(s)"))
  }

  private def loadMigrations(uri: Uri): F[List[ArtifactChange]] =
    logger.debug(s"Loading artifact migrations from $uri") >>
      fileAlg.readUri(uri).flatMap(decodeMigrations(_, uri)).map(_.changes)

  private def decodeMigrations(content: String, uri: Uri): F[ArtifactChanges] =
    F.fromEither(decode[ArtifactChanges](content))
      .adaptErr(new Throwable(s"Failed to load artifact migrations from ${uri.renderString}", _))
}

object ArtifactMigrationsLoader {
  val defaultArtifactMigrationsUrl: Uri = Uri.unsafeFromString(
    s"${org.scalasteward.core.BuildInfo.gitHubUserContent}/modules/core/src/main/resources/artifact-migrations.v2.conf"
  )
}
