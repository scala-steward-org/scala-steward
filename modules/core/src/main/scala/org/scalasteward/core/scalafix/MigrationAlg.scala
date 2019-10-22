/*
 * Copyright 2018-2019 Scala Steward contributors
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
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.io.WorkspaceAlg
import cats.implicits._
import cats.Monad
import io.circe.parser._
import io.chrisdavenport.log4cats.Logger

trait MigrationAlg[F[_]] {
  def loadMigrations(repo: Repo): F[List[Migration]]
}

object MigrationAlg {

  def create[F[_]](
      implicit fileAlg: FileAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      logger: Logger[F],
      F: Monad[F]
  ): MigrationAlg[F] = new MigrationAlg[F] {
    override def loadMigrations(repo: Repo): F[List[Migration]] =
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        migrationsFile <- fileAlg.readFile(repoDir / ".scalafix-migrations.conf")
        migrations <- migrationsFile
          .traverse(parse(_).flatMap(_.as[List[Migration]]))
          .fold(
            _ => logger.warn("Failed to parse migrations file") >> F.pure(List.empty[Migration]),
            x => F.pure(x.combineAll)
          )
      } yield migrations
  }
}
