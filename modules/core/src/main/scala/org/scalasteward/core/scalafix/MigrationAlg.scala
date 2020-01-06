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

import org.scalasteward.core.io.FileAlg
import cats.implicits._
import cats.Monad
import io.circe.parser._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Update
import org.scalasteward.core.scalafix

trait MigrationAlg[F[_]] {
  def loadMigrations: F[List[Migration]]
  def findMigrations(update: Update): F[List[Migration]]
}

object MigrationAlg {
  def create[F[_]](
      implicit fileAlg: FileAlg[F],
      logger: Logger[F],
      F: Monad[F],
      config: Config
  ): MigrationAlg[F] = new MigrationAlg[F] {
    override def loadMigrations: F[List[Migration]] =
      for {
        fileContents <- config.scalafixMigrations.flatTraverse(fileAlg.readFile)
        defaultMigrations = migrations
        allMigrations <- fileContents
          .traverse(parse(_).flatMap(_.as[ScalafixMigrations]))
          .fold(
            _ => logger.warn("Failed to parse migrations file") >> defaultMigrations.pure[F],
            _.fold(defaultMigrations)(_.migrations(defaultMigrations)).pure[F]
          )
      } yield allMigrations

    override def findMigrations(update: Update): F[List[Migration]] =
      loadMigrations.map(scalafix.findMigrations(_, update))
  }
}
