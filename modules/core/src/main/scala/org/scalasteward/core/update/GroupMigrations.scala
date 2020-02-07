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

package org.scalasteward.core.update

import cats.implicits._
import org.scalasteward.core.data._
import org.scalasteward.core.application.Config
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.util.Nel
import io.circe.config.parser
import io.circe.Decoder
import io.circe.generic.extras.{semiauto, Configuration}
import cats.effect.Sync
import fs2.Stream

trait GroupMigrations[F[_]] {
  def findUpdateWithNewerGroupId(dependency: Dependency): Option[Update.Single]
}

object GroupMigrations {
  def create[F[_]: Sync](
      implicit fileAlg: FileAlg[F],
      config: Config
  ): F[GroupMigrations[F]] = {
    val migrationSources =
      Stream
        .emit("group-migrations.conf")
        .evalMap(org.scalasteward.core.io.readResource[F](_)) ++
        config.groupMigrations
          .foldMap(Stream.emit)
          .evalMap(fileAlg.readFile(_))
          .evalMap(
            _.toRight(new Throwable("Couldn't read the file with custom group migrations"))
              .liftTo[F]
          )

    migrationSources
      .evalMap(
        parser
          .decode[GroupIdChanges](_)
          .leftMap(e => new Throwable("Couldn't decode migrations file", e))
          .liftTo[F]
      )
      .flatMap(changes => Stream.emits(changes.changes))
      .compile
      .toList
      .map { migrations =>
        new GroupMigrations[F] {
          def findUpdateWithNewerGroupId(dependency: Dependency): Option[Update.Single] =
            migrateGroupId(dependency, migrations)
        }
      }
  }

  def migrateGroupId(
      dependency: Dependency,
      migrations: List[GroupIdChange]
  ): Option[Update.Single] =
    migrations.view
      .filter(_.before === dependency.groupId)
      .find(_.artifactId === dependency.artifactId.name)
      .map { migration =>
        Update.Single(
          CrossDependency(dependency),
          Nel.one(migration.initialVersion),
          Some(migration.after)
        )
      }

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  final case class GroupIdChanges(changes: List[GroupIdChange])
  object GroupIdChanges {
    implicit val decoder: Decoder[GroupIdChanges] =
      semiauto.deriveConfiguredDecoder
  }

  final case class GroupIdChange(
      before: GroupId,
      after: GroupId,
      artifactId: String,
      initialVersion: String
  )

  object GroupIdChange {

    implicit val decoder: Decoder[GroupIdChange] =
      semiauto.deriveConfiguredDecoder
  }
}
