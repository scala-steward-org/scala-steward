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

package org.scalasteward.core.forge

import better.files.File
import cats.effect.Sync
import cats.syntax.all.*
import org.http4s.Uri.UserInfo
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Request, Uri}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

class BasicAuthAlg[F[_]](apiUri: Uri, login: String, gitAskPass: File)(implicit
    F: Sync[F],
    workspaceAlg: WorkspaceAlg[F],
    processAlg: ProcessAlg[F]
) extends ForgeAuthAlg[F] {
  protected lazy val userInfo: F[UserInfo] = for {
    rootDir <- workspaceAlg.rootDir
    userInfo = UserInfo(login, None)
    urlWithUser = util.uri.withUserInfo.replace(userInfo)(apiUri).renderString
    prompt = s"Password for '$urlWithUser': "
    output <- processAlg.exec(Nel.of(gitAskPass.pathAsString, prompt), rootDir)
    password = output.mkString.trim
  } yield UserInfo(login, Some(password))

  override def authenticateApi(req: Request[F]): F[Request[F]] =
    userInfo.map {
      case UserInfo(username, Some(password)) =>
        req.putHeaders(Authorization(BasicCredentials(username, password)))
      case _ => req
    }

  override def authenticateGit(uri: Uri): F[Uri] =
    userInfo.map(user => util.uri.withUserInfo.replace(user)(uri))

  override def accessibleRepos: F[List[Repo]] = F.pure(List.empty)
}
