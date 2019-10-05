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

package org.scalasteward.core.repocache

import cats.Monad
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import java.util.concurrent.TimeUnit
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.repocache.RefreshErrorAlg.Entry
import org.scalasteward.core.util.DateTimeAlg
import org.scalasteward.core.vcs.data.Repo
import scala.concurrent.duration._

final class RefreshErrorAlg[F[_]](kvStore: KeyValueStore[F, Repo, Entry])(
    implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  def failedRecently(repo: Repo): F[Boolean] =
    dateTimeAlg.currentTimeMillis.flatMap { now =>
      val maybeEntry = kvStore.modify(repo) {
        case Some(entry) if entry.hasExpired(now) => None
        case res                                  => res
      }
      maybeEntry.map(_.isDefined)
    }

  def persistError(repo: Repo, throwable: Throwable): F[Unit] =
    dateTimeAlg.currentTimeMillis.flatMap { now =>
      kvStore.put(repo, Entry(now, throwable.getMessage))
    }
}

object RefreshErrorAlg {
  final case class Entry(failedAt: Long, message: String) {
    def hasExpired(now: Long): Boolean = {
      val timeToLive = 7.days
      FiniteDuration(now - failedAt, TimeUnit.MILLISECONDS) > timeToLive
    }
  }

  object Entry {
    implicit val entryDecoder: Decoder[Entry] =
      deriveDecoder

    implicit val entryEncoder: Encoder[Entry] =
      deriveEncoder
  }
}
