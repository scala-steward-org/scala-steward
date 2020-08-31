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
import io.circe.Decoder
import io.circe.config.parser
import io.circe.generic.extras.{semiauto, Configuration}
import org.scalasteward.core.application.Config
import org.scalasteward.core.data._
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.util.{MonadThrowable, Nel}

trait GroupMigrations {
  def findUpdateWithNewerGroupId(dependency: Dependency): Option[Update.Single]
}

object GroupMigrations {
  def create[F[_]: MonadThrowable](implicit
      fileAlg: FileAlg[F],
      config: Config
  ): F[GroupMigrations] = {
    val migrationSources = {

      val fromParameters: F[Option[String]] =
        config.groupMigrations.traverse(fileAlg.readFile(_)).flatMap {
          case None => none[String].pure[F]
          case Some(None) =>
            new Throwable("Couldn't read the file with custom group migrations")
              .raiseError[F, Option[String]]
          case Some(Some(text)) => text.some.pure[F]
        }

      List(
        fileAlg.readResource("group-migrations.conf").map(List(_)),
        fromParameters.map(_.toList)
      ).flatSequence
    }

    val decodeFile: String => F[GroupIdChanges] = parser
      .decode[GroupIdChanges](_)
      .leftMap(e => new Throwable("Couldn't decode migrations file", e))
      .liftTo[F]

    migrationSources
      .flatMap(_.traverse(decodeFile))
      .map(_.flatMap(_.changes))
      .map(migrations => dependency => migrateGroupId(dependency, migrations))
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
