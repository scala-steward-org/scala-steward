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
import io.chrisdavenport.log4cats.Logger
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, KeyEncoder}
import java.util.concurrent.TimeUnit

import org.scalasteward.core.application.Config
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{Dependency, Resolver, Version}
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
  def getVersions(dependency: Dependency, extraResolvers: List[Resolver]): F[List[Version]] =
    for {
      now <- dateTimeAlg.currentTimeMillis
      module = Module(dependency)
      versions <- kvStore.get(module).flatMap {
        case Some(entry) if entry.age(now) <= config.cacheTtl => F.pure(entry.versions.sorted)
        case maybeEntry =>
          val getAndPut = rateLimiter.limit {
            coursierAlg.getVersions(dependency, extraResolvers).flatTap { versions =>
              kvStore.put(module, Entry(now, versions))
            }
          }
          getAndPut.handleErrorWith { throwable =>
            val message = s"Failed to get versions of $dependency"
            val versions = maybeEntry.map(_.versions).getOrElse(List.empty).sorted
            logger.error(throwable)(message).as(versions)
          }
      }
    } yield versions

  def getNewerVersions(dependency: Dependency, extraResolvers: List[Resolver]): F[List[Version]] = {
    val current = Version(dependency.version)
    getVersions(dependency, extraResolvers).map(_.filter(_ > current))
  }
}

object VersionsCacheAlg {
  final case class Module(dependency: Dependency) {
    def key: String =
      dependency.groupId.value.replace('.', '/') + "/" +
        dependency.artifactId.crossName +
        dependency.scalaVersion.fold("")("_" + _.value) +
        dependency.sbtVersion.fold("")("_" + _.value)
  }

  object Module {
    implicit val moduleKeyEncoder: KeyEncoder[Module] =
      KeyEncoder.instance(_.key)
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
