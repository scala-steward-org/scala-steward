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

import cats.Monad
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import java.util.concurrent.TimeUnit
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.ExcludeAlg._
import org.scalasteward.core.util.DateTimeAlg
import scala.concurrent.duration._

final class ExcludeAlg[F[_]](
    kvStore: KeyValueStore[F, String, List[Entry]]
)(
    implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  private val key = "excluded"

  def excludeTemporarily(dependencies: List[Dependency]): F[Unit] =
    dateTimeAlg.currentTimeMillis.flatMap { now =>
      kvStore.update(key) {
        _.getOrElse(List.empty).filter(_.isExcluded(now)) ++ dependencies.map(Entry(_, now))
      }
    }

  def removeExcluded(dependencies: List[Dependency]): F[List[Dependency]] =
    for {
      now <- dateTimeAlg.currentTimeMillis
      maybeExcluded <- kvStore.get(key)
      excluded = maybeExcluded.getOrElse(List.empty).filter(_.isExcluded(now))
      _ <- kvStore.put(key, excluded)
    } yield dependencies.diff(excluded.map(_.dependency))
}

object ExcludeAlg {
  final case class Entry(dependency: Dependency, excludedAt: Long) {
    def isExcluded(now: Long): Boolean =
      FiniteDuration(now - excludedAt, TimeUnit.MILLISECONDS) <= 21.days
  }

  object Entry {
    implicit val entryDecoder: Decoder[Entry] =
      deriveDecoder

    implicit val entryEncoder: Encoder[Entry] =
      deriveEncoder
  }
}
