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

import cats.Applicative

/** A [[KeyValueStore]] decorator whose writes are no-ops, leaving the persisted state untouched.
  * Reads are delegated to `underlying`.
  *
  * This is used in `--dry-run` mode to avoid recording fabricated pull requests, which would
  * otherwise poison subsequent real runs (e.g. cooldown and obsolete-PR handling).
  */
final class DryRunKeyValueStore[F[_], K, V](underlying: KeyValueStore[F, K, V])(implicit
    F: Applicative[F]
) extends KeyValueStore[F, K, V] {
  override def get(key: K): F[Option[V]] = underlying.get(key)

  override def set(key: K, value: Option[V]): F[Unit] = F.unit
}
