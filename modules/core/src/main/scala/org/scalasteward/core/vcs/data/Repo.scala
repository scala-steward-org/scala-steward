/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.vcs.data

import cats.Eq
import io.circe.KeyEncoder
import org.scalasteward.core.git.Branch

final case class Repo(
    owner: String,
    repo: String,
    branch: Option[Branch] = None
) {
  def show: String =
    owner + "/" + repo + branch.fold("")(":" + _.name)

  def toPath: String =
    owner + "/" + repo + branch.fold("")("/" + _.name)
}

object Repo {
  def parse(s: String): Option[Repo] = {
    val regex = """-\s+([^:]+)/([^/:]+)(:.+)?""".r
    s match {
      case regex(owner, repo, branch) =>
        Some(Repo(owner.trim, repo.trim, Option(branch).map(b => Branch(b.tail.trim))))
      case _ =>
        None
    }
  }

  implicit val repoEq: Eq[Repo] =
    Eq.fromUniversalEquals

  implicit val repoKeyEncoder: KeyEncoder[Repo] =
    KeyEncoder.instance(_.toPath)
}
