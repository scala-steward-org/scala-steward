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

package org.scalasteward.core.forge.data

import cats.ApplicativeThrow
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.intellijThisImportIsUsed
import org.scalasteward.core.util.uri.uriDecoder

final case class RepoOut(
    name: String,
    owner: UserOut,
    parent: Option[RepoOut],
    clone_url: Uri,
    default_branch: Branch,
    archived: Boolean = false
) {
  def parentOrRaise[F[_]](implicit F: ApplicativeThrow[F]): F[RepoOut] =
    parent.fold(F.raiseError[RepoOut](new Throwable(s"repo ${repo.show} has no parent")))(F.pure)

  def repo: Repo =
    Repo(owner.login, name)

  def withBranch(branch: Branch): RepoOut =
    copy(default_branch = branch, parent = parent.map(_.withBranch(branch)))

}

object RepoOut {
  implicit val repoOutDecoder: Decoder[RepoOut] =
    deriveDecoder

  intellijThisImportIsUsed(uriDecoder)
}
