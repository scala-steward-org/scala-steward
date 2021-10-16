/*
 * Copyright 2018-2021 Scala Steward contributors
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
  def show: String = branch match {
    case Some(value) => s"$owner/$repo:${value.name}"
    case None        => s"$owner/$repo"
  }

  def toPath: String = branch match {
    case Some(value) => s"$owner/$repo/${value.name}"
    case None        => s"$owner/$repo"
  }
}

object Repo {
  implicit val repoEq: Eq[Repo] =
    Eq.fromUniversalEquals

  implicit val repoKeyEncoder: KeyEncoder[Repo] =
    KeyEncoder.instance {
      case Repo(owner, repo, Some(branch)) => owner + "/" + repo + "/" + branch.name
      case Repo(owner, repo, None)         => owner + "/" + repo
    }
}
