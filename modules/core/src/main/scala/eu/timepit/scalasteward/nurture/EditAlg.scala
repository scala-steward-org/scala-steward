/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.nurture

import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.WorkspaceAlg
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt.SbtAlg
import eu.timepit.scalasteward.{ioLegacy, scalafix}
import eu.timepit.scalasteward.util.Nel

trait EditAlg[F[_]] {
  def applyUpdate(repo: Repo, update: Update): F[Unit]
}

object EditAlg {
  def create[F[_]](
      implicit
      sbtAlg: SbtAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Sync[F]
  ): EditAlg[F] =
    new EditAlg[F] {
      override def applyUpdate(repo: Repo, update: Update): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- Nel.fromList(scalafix.findMigrations(update)) match {
            case Some(migrations) => sbtAlg.runMigrations(repo, migrations)
            case None             => F.unit
          }
          _ <- ioLegacy.updateDir(repoDir, update)
        } yield ()
    }
}
