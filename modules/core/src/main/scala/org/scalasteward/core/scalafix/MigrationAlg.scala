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

package org.scalasteward.core.scalafix

import better.files.File
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import io.circe.config.parser.decode
import org.scalasteward.core.data.{Update, Version}
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.util.{ApplicativeThrowable, MonadThrowable}

trait MigrationAlg {
  def findMigrations(update: Update): List[Migration]
}

object MigrationAlg {
  def create[F[_]](extraMigrations: Option[File])(implicit
      fileAlg: FileAlg[F],
      F: Sync[F]
  ): F[MigrationAlg] =
    loadMigrations(extraMigrations).map { migrations =>
      new MigrationAlg {
        override def findMigrations(update: Update): List[Migration] =
          findMigrationsImpl(migrations, update)
      }
    }

  def loadMigrations[F[_]](
      extraMigrations: Option[File]
  )(implicit fileAlg: FileAlg[F], F: MonadThrowable[F]): F[List[Migration]] =
    for {
      default <-
        fileAlg
          .readResource("scalafix-migrations.conf")
          .flatMap(decodeMigrations[F](_, "default"))
      maybeExtra <- OptionT(extraMigrations.flatTraverse(fileAlg.readFile))
        .semiflatMap(decodeMigrations[F](_, "extra"))
        .value
      migrations = maybeExtra match {
        case Some(extra) if extra.disableDefaults => extra.migrations
        case Some(extra)                          => default.migrations ++ extra.migrations
        case None                                 => default.migrations
      }
    } yield migrations

  private def decodeMigrations[F[_]](content: String, tpe: String)(implicit
      F: ApplicativeThrowable[F]
  ): F[ScalafixMigrations] =
    F.fromEither(decode[ScalafixMigrations](content))
      .adaptErr(new Throwable(s"Failed to load $tpe Scalafix migrations", _))

  private def findMigrationsImpl(
      givenMigrations: List[Migration],
      update: Update
  ): List[Migration] =
    givenMigrations.filter { migration =>
      update.groupId === migration.groupId &&
      migration.artifactIds.exists(re =>
        update.artifactIds.exists(artifactId => re.r.findFirstIn(artifactId.name).isDefined)
      ) &&
      Version(update.currentVersion) < migration.newVersion &&
      Version(update.newerVersions.head) >= migration.newVersion
    }
}
