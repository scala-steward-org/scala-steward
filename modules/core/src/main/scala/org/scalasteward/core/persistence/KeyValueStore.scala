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

import cats.syntax.all.*
import cats.{FlatMap, Functor}

trait KeyValueStore[F[_], K, V] {
  def get(key: K): F[Option[V]]

  def set(key: K, value: Option[V]): F[Unit]

  final def getOrElse(key: K, default: => V)(implicit F: Functor[F]): F[V] =
    get(key).map(_.getOrElse(default))

  final def modifyF(key: K)(f: Option[V] => F[Option[V]])(implicit F: FlatMap[F]): F[Option[V]] =
    get(key).flatMap(f).flatTap(set(key, _))

  final def put(key: K, value: V): F[Unit] =
    set(key, Some(value))
}
