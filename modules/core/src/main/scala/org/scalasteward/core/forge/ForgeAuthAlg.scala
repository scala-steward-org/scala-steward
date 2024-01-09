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

package org.scalasteward.core.forge

import cats.Monad
import cats.syntax.all._
import org.http4s.Uri.UserInfo
import org.scalasteward.core.application.Config.{ForgeCfg, GitCfg}
import org.scalasteward.core.forge.data.AuthenticatedUser
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

final class ForgeAuthAlg[F[_]](gitCfg: GitCfg, forgeCfg: ForgeCfg)(implicit
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def forgeUser: F[AuthenticatedUser] =
    for {
      rootDir <- workspaceAlg.rootDir
      userInfo = UserInfo(forgeCfg.login, None)
      urlWithUser = util.uri.withUserInfo.replace(userInfo)(forgeCfg.apiHost).renderString
      prompt = s"Password for '$urlWithUser': "
      output <- processAlg.exec(Nel.of(gitCfg.gitAskPass.pathAsString, prompt), rootDir)
      password = output.mkString.trim
    } yield AuthenticatedUser(forgeCfg.login, password)
}
