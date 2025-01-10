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

package org.scalasteward.core.edit.scalafix

import cats.MonadThrow
import cats.syntax.all.*
import io.circe.config.parser.decode
import org.http4s.Uri
import org.scalasteward.core.application.Config.ScalafixCfg
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoader.*
import org.scalasteward.core.io.FileAlg
import org.typelevel.log4cats.Logger

final class ScalafixMigrationsLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def createFinder(config: ScalafixCfg): F[ScalafixMigrationsFinder] =
    loadAll(config).map(new ScalafixMigrationsFinder(_))

  def loadAll(config: ScalafixCfg): F[List[ScalafixMigration]] = {
    val maybeDefaultMigrationsUrl =
      Option.unless(config.disableDefaults)(defaultScalafixMigrationsUrl)
    (maybeDefaultMigrationsUrl.toList ++ config.migrations)
      .flatTraverse(loadMigrations)
      .flatTap(migrations => logger.info(s"Loaded ${migrations.size} Scalafix migration(s)"))
  }

  private def loadMigrations(uri: Uri): F[List[ScalafixMigration]] =
    logger.debug(s"Loading Scalafix migrations from $uri") >>
      fileAlg.readUri(uri).flatMap(decodeMigrations(_, uri)).map(_.migrations)

  private def decodeMigrations(content: String, uri: Uri): F[ScalafixMigrations] =
    F.fromEither(decode[ScalafixMigrations](content))
      .adaptErr(new Throwable(s"Failed to load Scalafix migrations from ${uri.renderString}", _))
}

object ScalafixMigrationsLoader {
  val defaultScalafixMigrationsUrl: Uri = Uri.unsafeFromString(
    s"${org.scalasteward.core.BuildInfo.gitHubUserContent}/modules/core/src/main/resources/scalafix-migrations.conf"
  )
}
