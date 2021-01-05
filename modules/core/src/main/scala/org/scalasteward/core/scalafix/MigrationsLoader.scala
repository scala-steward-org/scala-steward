/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.scalafix

import cats.MonadThrow
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.circe.config.parser.decode
import org.http4s.Uri
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalasteward.core.application.Config.ScalafixCfg
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.scalafix.MigrationsLoader._

final class MigrationsLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def loadAll(config: ScalafixCfg): F[List[Migration]] = {
    val maybeDefaultMigrationsUrl =
      Option.unless(config.disableDefaults)(defaultScalafixMigrationsUrl)
    (maybeDefaultMigrationsUrl.toList ++ config.migrations)
      .flatTraverse(loadMigrations)
      .flatTap(migrations => logger.info(s"Loaded ${migrations.size} Scalafix migrations"))
  }

  private def loadMigrations(uri: Uri): F[List[Migration]] =
    logger.debug(s"Loading Scalafix migrations from $uri") >>
      fileAlg.readUri(uri).flatMap(decodeMigrations(_, uri)).map(_.migrations)

  private def decodeMigrations(content: String, uri: Uri): F[ScalafixMigrations] =
    F.fromEither(decode[ScalafixMigrations](content))
      .adaptErr(new Throwable(s"Failed to load Scalafix migrations from ${uri.renderString}", _))
}

object MigrationsLoader {
  val defaultScalafixMigrationsUrl: Uri =
    uri"https://raw.githubusercontent.com/scala-steward-org/scala-steward/master/modules/core/src/main/resources/scalafix-migrations.conf"
}
