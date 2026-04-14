package org.scalasteward.core.forge.bitbucket

import better.files.File
import cats.effect.Sync
import cats.implicits.toFunctorOps
import org.http4s.Uri
import org.http4s.Uri.UserInfo
import org.scalasteward.core.forge.BasicAuthAlg
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util

class BitbucketAuthAlg[F[_]](apiUri: Uri, login: String, gitAskPass: File)(implicit
    F: Sync[F],
    workspaceAlg: WorkspaceAlg[F],
    processAlg: ProcessAlg[F]
) extends BasicAuthAlg[F](apiUri, login, gitAskPass) {
  override def authenticateGit(uri: Uri): F[Uri] =
    userInfo.map(user =>
      util.uri.withUserInfo.replace(UserInfo("x-bitbucket-api-token-auth", user.password))(uri)
    )
}
