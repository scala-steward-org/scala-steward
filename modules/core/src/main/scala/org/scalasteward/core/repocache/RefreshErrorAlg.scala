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

package org.scalasteward.core.repocache

import cats.effect.MonadThrow
import cats.syntax.all._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.repocache.RefreshErrorAlg.Entry
import org.scalasteward.core.util.dateTime.showDuration
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vcs.data.Repo
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

final class RefreshErrorAlg[F[_]](kvStore: KeyValueStore[F, Repo, Entry])(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: MonadThrow[F]
) {
  def skipIfFailedRecently[A](repo: Repo)(fa: F[A]): F[A] =
    failedRecently(repo).flatMap {
      case None => fa
      case Some(fd) =>
        val msg = s"Skipping due to previous error for ${showDuration(fd)}"
        F.raiseError[A](new Throwable(msg) with NoStackTrace)
    }

  def persistError[A](repo: Repo)(fa: F[A]): F[A] =
    fa.handleErrorWith { t =>
      dateTimeAlg.currentTimestamp.flatMap { now =>
        kvStore.put(repo, Entry(now, t.getMessage))
      } >> F.raiseError[A](t)
    }

  private def failedRecently(repo: Repo): F[Option[FiniteDuration]] =
    kvStore.get(repo).flatMap {
      case None => F.pure(None)
      case Some(entry) =>
        dateTimeAlg.currentTimestamp.flatMap { now =>
          entry.expiresIn(now) match {
            case some @ Some(_) => F.pure(some)
            case None           => kvStore.set(repo, None).as(None)
          }
        }
    }
}

object RefreshErrorAlg {
  final case class Entry(failedAt: Timestamp, message: String) {
    def expiresIn(now: Timestamp): Option[FiniteDuration] = {
      val duration = 7.days - failedAt.until(now)
      if (duration.length > 0L) Some(duration) else None
    }
  }

  object Entry {
    implicit val entryCodec: Codec[Entry] =
      deriveCodec
  }
}
