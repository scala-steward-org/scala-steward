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

package org.scalasteward.core.vcs.github

import cats.Applicative
import cats.syntax.functor._
import org.http4s.{Header, Uri}
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.ci._

class GitHubAppApiAlg[F[_]: Applicative](
    gitHubApiHost: Uri
)(implicit
    client: HttpJsonClient[F]
) {

  private[this] val acceptHeader =
    Header.Raw(ci"Accept", "application/vnd.github.v3+json")

  private[this] def addHeaders(jwt: String): client.ModReq =
    req =>
      Applicative[F].point(
        req.putHeaders(
          Header.Raw(ci"Authorization", s"Bearer $jwt"),
          acceptHeader
        )
      )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-installations-for-the-authenticated-app]]
    */
  def installations(jwt: String): F[List[InstallationOut]] =
    client
      .getAll[List[InstallationOut]](
        (gitHubApiHost / "app" / "installations").withQueryParam("per_page", 100),
        addHeaders(jwt)
      )
      .map(_.flatten)

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#create-an-installation-access-token-for-an-app]] */
  def accessToken(jwt: String, installationId: Long): F[TokenOut] =
    client.post(
      gitHubApiHost / "app" / "installations" / installationId.toString / "access_tokens",
      addHeaders(jwt)
    )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-repositories-accessible-to-the-app-installation]]
    */
  def repositories(token: String): F[RepositoriesOut] =
    client
      .getAll[RepositoriesOut](
        (gitHubApiHost / "installation" / "repositories").withQueryParam("per_page", 100),
        req =>
          Applicative[F].point(
            req.putHeaders(
              Header.Raw(ci"Authorization", s"token $token"),
              acceptHeader
            )
          )
      )
      .map(values =>
        RepositoriesOut(
          values.flatMap(_.repositories)
        )
      )
}
