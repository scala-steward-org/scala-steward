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

package org.scalasteward.core.update

import cats.MonadThrow
import cats.syntax.all._
import io.circe.Decoder
import io.circe.config.parser
import io.circe.generic.extras.{semiauto, Configuration}
import org.scalasteward.core.application.Config
import org.scalasteward.core.data._
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.util.Nel

trait ArtifactMigrations {
  def findUpdateWithRenamedArtifact(dependency: Dependency): Option[Update.Single]
}

object ArtifactMigrations {
  def create[F[_]](config: Config)(implicit
      fileAlg: FileAlg[F],
      F: MonadThrow[F]
  ): F[ArtifactMigrations] = {
    val migrationSources = {

      val fromParameters: F[Option[String]] =
        config.artifactMigrations.traverse(fileAlg.readFile).flatMap {
          case None => none[String].pure[F]
          case Some(None) =>
            new Throwable("Couldn't read the file with custom artifact migrations")
              .raiseError[F, Option[String]]
          case Some(Some(text)) => text.some.pure[F]
        }

      List(
        fileAlg.readResource("artifact-migrations.conf").map(List(_)),
        fromParameters.map(_.toList)
      ).flatSequence
    }

    val decodeFile: String => F[ArtifactChanges] = parser
      .decode[ArtifactChanges](_)
      .leftMap(e => new Throwable("Couldn't decode migrations file", e))
      .liftTo[F]

    migrationSources
      .flatMap(_.traverse(decodeFile))
      .map(_.flatMap(_.changes))
      .map(migrations => dependency => migrateArtifact(dependency, migrations))
  }

  def migrateArtifact(
      dependency: Dependency,
      migrations: List[ArtifactChange]
  ): Option[Update.Single] =
    migrations.view
      .find { migration =>
        (migration.groupIdBefore, migration.artifactIdBefore) match {
          case (Some(groupId), Some(artifactId)) =>
            groupId === dependency.groupId &&
              artifactId === dependency.artifactId.name
          case (Some(groupId), _) =>
            groupId === dependency.groupId &&
              migration.artifactIdAfter === dependency.artifactId.name
          case (_, Some(artifactId)) =>
            migration.groupIdAfter === dependency.groupId &&
              artifactId === dependency.artifactId.name
          case _ => false
        }
      }
      .map { migration =>
        Update.Single(
          CrossDependency(dependency),
          Nel.one(migration.initialVersion),
          Some(migration.groupIdAfter),
          Some(migration.artifactIdAfter)
        )
      }

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  final case class ArtifactChanges(changes: List[ArtifactChange])

  object ArtifactChanges {
    implicit val decoder: Decoder[ArtifactChanges] =
      semiauto.deriveConfiguredDecoder
  }

  final case class ArtifactChange(
      groupIdBefore: Option[GroupId],
      groupIdAfter: GroupId,
      artifactIdBefore: Option[String],
      artifactIdAfter: String,
      initialVersion: String
  )

  object ArtifactChange {

    implicit val decoder: Decoder[ArtifactChange] =
      semiauto.deriveConfiguredDecoder
  }
}
