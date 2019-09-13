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

package org.scalasteward.core.update.json

import cats.FlatMap
import cats.implicits._
import org.scalasteward.core.data.Update
import org.scalasteward.core.update.UpdateRepository
import org.scalasteward.core.util.JsonKeyValueStore

final class JsonUpdateRepository[F[_]: FlatMap](
    kvStore: JsonKeyValueStore[F, String, List[Update.Single]]
) extends UpdateRepository[F] {
  private val key = "updates"

  override def deleteAll: F[Unit] =
    kvStore.write(Map.empty)

  override def saveMany(updates: List[Update.Single]): F[Unit] =
    kvStore
      .getOrElse(key, List.empty)
      .map(_ ++ updates)
      .flatMap(list => kvStore.write(Map(key -> list)))
}
