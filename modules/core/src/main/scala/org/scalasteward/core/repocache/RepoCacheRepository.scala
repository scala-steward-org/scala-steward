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

import cats.Functor
import cats.implicits._
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.vcs.data.Repo

trait RepoCacheRepository[F[_]] {
  def findCache(repo: Repo): F[Option[RepoCache]]

  def updateCache(repo: Repo, repoCache: RepoCache): F[Unit]

  def getDependencies(repos: List[Repo]): F[List[Dependency]]

  final def findSha1(repo: Repo)(implicit F: Functor[F]): F[Option[Sha1]] =
    findCache(repo).map(_.map(_.sha1))
}
