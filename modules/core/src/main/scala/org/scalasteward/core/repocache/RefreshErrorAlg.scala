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

package org.scalasteward.core.repocache

import cats.Monad
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.repocache.RefreshErrorAlg.Entry
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vcs.data.Repo
import scala.concurrent.duration._

final class RefreshErrorAlg[F[_]](kvStore: KeyValueStore[F, Repo, Entry])(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  def failedRecently(repo: Repo): F[Boolean] =
    dateTimeAlg.currentTimestamp.flatMap { now =>
      val maybeEntry = kvStore.modify(repo) {
        case Some(entry) if entry.hasExpired(now) => None
        case res                                  => res
      }
      maybeEntry.map(_.isDefined)
    }

  def persistError(repo: Repo, throwable: Throwable): F[Unit] =
    dateTimeAlg.currentTimestamp.flatMap { now =>
      kvStore.put(repo, Entry(now, throwable.getMessage))
    }
}

object RefreshErrorAlg {
  final case class Entry(failedAt: Timestamp, message: String) {
    def hasExpired(now: Timestamp): Boolean =
      failedAt.until(now) > 7.days
  }

  object Entry {
    implicit val entryCodec: Codec[Entry] =
      deriveCodec
  }
}
