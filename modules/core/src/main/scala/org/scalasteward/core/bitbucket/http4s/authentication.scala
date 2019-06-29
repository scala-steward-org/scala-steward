package org.scalasteward.core.bitbucket.http4s

import cats.Applicative
import cats.implicits._
import org.http4s.{BasicCredentials, Request}
import org.http4s.headers.Authorization
import org.scalasteward.core.vcs.data.AuthenticatedUser

object authentication {
  def addCredentials[F[_]: Applicative](user: AuthenticatedUser): Request[F] => F[Request[F]] =
    _.putHeaders(Authorization(BasicCredentials(user.login, user.accessToken))).pure[F]
}
