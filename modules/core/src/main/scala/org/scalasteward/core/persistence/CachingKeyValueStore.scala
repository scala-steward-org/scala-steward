/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.persistence

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import cats.{Eq, Monad}

final class CachingKeyValueStore[F[_], K, V](
    underlying: KeyValueStore[F, K, V],
    cache: Ref[F, Option[(K, Option[V])]]
)(implicit kEq: Eq[K], F: Monad[F])
    extends KeyValueStore[F, K, V] {
  override def get(key: K): F[Option[V]] =
    cache.get.flatMap {
      case Some((cachedKey, cachedValue)) if cachedKey === key =>
        F.pure(cachedValue)
      case _ =>
        underlying.get(key).flatTap(value => cache.set(Some(key -> value)))
    }

  override def set(key: K, value: Option[V]): F[Unit] =
    underlying.set(key, value) >> cache.set(Some(key -> value))
}

object CachingKeyValueStore {
  def wrap[F[_], K, V](
      underlying: KeyValueStore[F, K, V]
  )(implicit kEq: Eq[K], F: Sync[F]): F[KeyValueStore[F, K, V]] =
    Ref[F].of(Option.empty[(K, Option[V])]).map(new CachingKeyValueStore[F, K, V](underlying, _))
}
