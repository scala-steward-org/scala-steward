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

package org.scalasteward.core.coursier

import cats.FlatMap
import cats.implicits._
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, KeyEncoder}
import java.util.concurrent.TimeUnit
import org.scalasteward.core.coursier.VersionsCacheFacade.{Key, Value}
import org.scalasteward.core.data.{Dependency, Scope, Version}
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.util.{DateTimeAlg, RateLimiter}
import scala.concurrent.duration.FiniteDuration

/** Facade of Coursier's versions cache that keeps track of the instant
  * when versions of a dependency are updated. This information is used
  * to rate limit calls to Coursier that require a network call to
  * populate or refresh its cache. Calls that probably hit the cache
  * are unlimited.
  *
  * Note that resolvers are ignored when storing the instant of the
  * last update which could lead to unlimited calls to Coursier that
  * hit the network. This will happen for dependencies that have been
  * checked against different resolvers before.
  */
final class VersionsCacheFacade[F[_]](
    cacheTtl: FiniteDuration,
    store: KeyValueStore[F, Key, Value],
    rateLimiter: RateLimiter[F]
)(
    implicit
    coursierAlg: CoursierAlg[F],
    dateTimeAlg: DateTimeAlg[F],
    F: FlatMap[F]
) {
  def getVersions(dependency: Scope.Dependency, maxAge: Option[FiniteDuration]): F[List[Version]] =
    dateTimeAlg.currentTimeMillis.flatMap { now =>
      store.get(Key(dependency.value)).flatMap {
        case Some(value) if value.age(now) <= maxAge.getOrElse(cacheTtl) =>
          coursierAlg.getVersions(dependency)
        case _ =>
          rateLimiter.limit {
            coursierAlg.getVersionsFresh(dependency) <*
              store.put(Key(dependency.value), Value(now))
          }
      }
    }
}

object VersionsCacheFacade {
  final case class Key(dependency: Dependency) {
    override def toString: String =
      dependency.groupId.value.replace('.', '/') + "/" +
        dependency.artifactId.crossName +
        dependency.scalaVersion.fold("")("_" + _.value) +
        dependency.sbtVersion.fold("")("_" + _.value)
  }

  object Key {
    implicit val keyKeyEncoder: KeyEncoder[Key] =
      KeyEncoder.instance(_.toString)
  }

  final case class Value(updatedAt: Long) {
    def age(now: Long): FiniteDuration =
      FiniteDuration(now - updatedAt, TimeUnit.MILLISECONDS)
  }

  object Value {
    implicit val valueCodec: Codec[Value] =
      deriveCodec
  }
}
