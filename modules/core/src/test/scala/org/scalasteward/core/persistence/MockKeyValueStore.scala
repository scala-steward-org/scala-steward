package org.scalasteward.core.persistence

import cats.effect.{Ref, Sync}
import cats.syntax.functor._

class MockKeyValueStore[F[_]: Sync, K, V] extends KeyValueStore[F, K, V] {
  private val mapRef = Ref.unsafe[F, Map[K, Option[V]]](Map.empty)

  override def get(key: K): F[Option[V]] = mapRef.get.map(_.get(key).flatten)

  override def set(key: K, value: Option[V]): F[Unit] =
    mapRef.getAndUpdate(_.updated(key, value)).void
}
