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

package org.scalasteward.core.buildtool

import org.scalasteward.core.data.Scope
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait BuildToolAlg[F[_]] {
  def containsBuild(repo: Repo): F[Boolean]

  def getDependencies(repo: Repo): F[List[Scope.Dependencies]]

  def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit]
}
