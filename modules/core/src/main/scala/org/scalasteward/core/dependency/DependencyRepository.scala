/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.dependency

import org.scalasteward.core.git.Sha1
import org.scalasteward.core.vcs.data.Repo

trait DependencyRepository[F[_]] {
  def findSha1(repo: Repo): F[Option[Sha1]]

  def getDependencies(repos: List[Repo]): F[List[Dependency]]

  def setDependencies(repo: Repo, sha1: Sha1, dependencies: List[Dependency]): F[Unit]
}
