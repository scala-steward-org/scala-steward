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

package org.scalasteward.core.ammonite

import cats.implicits._
import cats.effect.Async
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util
import org.scalasteward.core.vcs.data.Repo

trait AmmoniteAlg[F[_]] {
  def getAmmoniteScriptDependencies(repo: Repo): F[List[Dependency]]
}

object AmmoniteAlg {
  def create[F[_]](
      implicit fileAlg: FileAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Async[F]
  ): AmmoniteAlg[F] =
    (repo: Repo) =>
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        ammoniteScripts <- fileAlg
          .walk(repoDir)
          .through(util.evalFilter(fileAlg.isRegularFile))
          .filter(_.extension.exists(_ === ".sc"))
          .compile
          .toList
        fileContents <- ammoniteScripts.traverse(fileAlg.readFile).map(_.flatten)
      } yield fileContents.flatMap(parseAmmoniteScript)
}
