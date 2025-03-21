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

package org.scalasteward.core.repocache

import org.scalasteward.core.data.Repo
import org.scalasteward.core.persistence.KeyValueStore

final class RepoCacheRepository[F[_]](kvStore: KeyValueStore[F, Repo, RepoCache]) {
  def findCache(repo: Repo): F[Option[RepoCache]] =
    kvStore.get(repo)

  def updateCache(repo: Repo, repoCache: RepoCache): F[Unit] =
    kvStore.put(repo, repoCache)
}
