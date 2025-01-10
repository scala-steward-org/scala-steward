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

package org.scalasteward.core.forge.gitlab

import better.files.File
import cats.effect.Sync
import cats.syntax.all.*
import org.http4s.Uri.UserInfo
import org.http4s.{Header, Request, Uri}
import org.scalasteward.core.forge.BasicAuthAlg
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.typelevel.ci.CIStringSyntax

class GitLabAuthAlg[F[_]](apiUri: Uri, login: String, gitAskPass: File)(implicit
    F: Sync[F],
    workspaceAlg: WorkspaceAlg[F],
    processAlg: ProcessAlg[F]
) extends BasicAuthAlg[F](apiUri, login, gitAskPass) {
  override def authenticateApi(req: Request[F]): F[Request[F]] =
    userInfo.map {
      case UserInfo(_, Some(password)) => req.putHeaders(Header.Raw(ci"Private-Token", password))
      case _                           => req
    }
}
