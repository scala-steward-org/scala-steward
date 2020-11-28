/*
 * Copyright 2018-2020 Scala Steward contributors
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
import org.http4s.{Header, Uri}
import org.scalasteward.core.util.HttpJsonClient

class GitHubAppApiAlg[F[_]: Applicative](
    gitHubApiHost: Uri
)(implicit
    client: HttpJsonClient[F]
) {

  private[this] val acceptHeader =
    Header("Accept", "application/vnd.github.v3+json")

  private[this] def addHeaders(jwt: String): client.ModReq =
    req =>
      Applicative[F].point(
        req.putHeaders(
          Header("Authorization", s"Bearer $jwt"),
          acceptHeader
        )
      )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-installations-for-the-authenticated-app]]
    *
    * TODO pagination use `page` query param
    */
  def installations(jwt: String): F[List[InstallationOut]] =
    client.get(
      (gitHubApiHost / "app" / "installations").withQueryParam("per_page", 100),
      addHeaders(jwt)
    )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#create-an-installation-access-token-for-an-app]] */
  def accessToken(jwt: String, installationId: Long): F[TokenOut] =
    client.post(
      gitHubApiHost / "app" / "installations" / installationId.toString / "access_tokens",
      addHeaders(jwt)
    )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-repositories-accessible-to-the-app-installation]]
    *
    * TODO pagination use `page` query param
    */
  def repositories(token: String): F[RepositoriesOut] =
    client
      .get(
        (gitHubApiHost / "installation" / "repositories").withQueryParam("per_page", 100),
        req =>
          Applicative[F].point(
            req.putHeaders(
              Header("Authorization", s"token ${token}"),
              acceptHeader
            )
          )
      )
}
