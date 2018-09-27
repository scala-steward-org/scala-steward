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

import io.circe.{KeyDecoder, KeyEncoder}

final case class Repo(
    owner: String,
    repo: String
) {
  def show: String = s"$owner:$repo"
}

object Repo {
  implicit val repoKeyDecoder: KeyDecoder[Repo] =
    KeyDecoder.instance { key =>
      val parts = key.split('/')
      if (parts.length == 2 && parts.forall(_.nonEmpty))
        Some(Repo(parts(0), parts(1)))
      else None
    }

  implicit val repoKeyEncoder: KeyEncoder[Repo] =
    KeyEncoder.instance(repo => s"${repo.owner}/${repo.repo}")
}
