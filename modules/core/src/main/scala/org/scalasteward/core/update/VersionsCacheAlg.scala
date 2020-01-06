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

package org.scalasteward.core.update

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, KeyEncoder}
import java.util.concurrent.TimeUnit
import org.scalasteward.core.application.Config
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.VersionsCacheAlg.{Entry, Module}
import org.scalasteward.core.util.{DateTimeAlg, MonadThrowable, RateLimiter}
import scala.concurrent.duration.FiniteDuration

final class VersionsCacheAlg[F[_]](
    kvStore: KeyValueStore[F, Module, Entry],
    rateLimiter: RateLimiter[F]
)(
    implicit
    config: Config,
    coursierAlg: CoursierAlg[F],
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def getVersions(dependency: Dependency): F[List[Version]] = {
    val module = Module(dependency)
    dateTimeAlg.currentTimeMillis.flatMap { now =>
      kvStore.get(module).flatMap {
        case Some(entry) if entry.age(now) <= config.cacheTtl => F.pure(entry.versions.sorted)
        case maybeEntry =>
          rateLimiter
            .limit(coursierAlg.getVersions(dependency))
            .flatTap(versions => kvStore.put(module, Entry(now, versions)))
            .handleErrorWith { throwable =>
              val message = s"Failed to get versions of $dependency"
              logger.error(throwable)(message).as(maybeEntry.map(_.versions).getOrElse(List.empty))
            }
      }
    }
  }

  def getNewerVersions(dependency: Dependency): F[List[Version]] = {
    val current = Version(dependency.version)
    getVersions(dependency).map(_.filter(_ > current))
  }
}

object VersionsCacheAlg {
  final case class Module(dependency: Dependency)

  object Module {
    implicit val moduleKeyEncoder: KeyEncoder[Module] =
      KeyEncoder.instance { m =>
        m.dependency.groupId.value + "/" + m.dependency.artifactId.crossName +
          m.dependency.scalaVersion.fold("")("_" + _.value) +
          m.dependency.sbtVersion.fold("")("_" + _.value)
      }
  }

  final case class Entry(updatedAt: Long, versions: List[Version]) {
    def age(now: Long): FiniteDuration =
      FiniteDuration(now - updatedAt, TimeUnit.MILLISECONDS)
  }

  object Entry {
    implicit val entryCodec: Codec[Entry] =
      deriveCodec
  }
}
