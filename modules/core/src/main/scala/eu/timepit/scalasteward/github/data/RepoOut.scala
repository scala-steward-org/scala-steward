/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.github.data

import cats.ApplicativeError
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.util.uriUtil.uriDecoder
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.Uri

final case class RepoOut(
    name: String,
    owner: UserOut,
    parent: Option[RepoOut],
    clone_url: Uri,
    default_branch: Branch
) {
  def parentOrRaise[F[_]](implicit F: ApplicativeError[F, Throwable]): F[RepoOut] =
    parent.fold(F.raiseError[RepoOut](new Throwable(s"repo $name has no parent")))(F.pure)

  def repo: Repo =
    Repo(owner.login, name)
}

object RepoOut {
  implicit val repoOutDecoder: Decoder[RepoOut] =
    deriveDecoder

  // prevent IntelliJ from removing the import of uriDecoder
  locally(uriDecoder)
}
