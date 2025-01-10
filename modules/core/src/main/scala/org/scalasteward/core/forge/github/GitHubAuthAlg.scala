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

package org.scalasteward.core.forge.github

import better.files.File
import cats.effect.Sync
import cats.implicits.*
import io.jsonwebtoken.Jwts
import java.io.FileReader
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey, Security}
import java.util.Date
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader
import org.http4s.Credentials.Token
import org.http4s.Uri.UserInfo
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Header, Request, Uri}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeAuthAlg
import org.scalasteward.core.util
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Using

final class GitHubAuthAlg[F[_]](
    apiUri: Uri,
    appId: Long,
    appKeyFile: File
)(implicit F: Sync[F], client: HttpJsonClient[F], logger: Logger[F])
    extends ForgeAuthAlg[F] {
  private val tokenTtl = 2.minutes

  private def parsePEMFile(pemFile: File): Array[Byte] =
    Using.resource(new PemReader(new FileReader(pemFile.toJava))) { reader =>
      reader.readPemObject().getContent
    }

  private def getPrivateKey(keyBytes: Array[Byte]): PrivateKey = {
    val kf = KeyFactory.getInstance("RSA")
    val keySpec = new PKCS8EncodedKeySpec(keyBytes)
    kf.generatePrivate(keySpec)
  }

  private def readPrivateKey(appKeyFile: File): PrivateKey = {
    val bytes = parsePEMFile(appKeyFile)
    getPrivateKey(bytes)
  }

  override def authenticateApi(req: Request[F]): F[Request[F]] = for {
    tokenRepos <- tokenRepos
    maybeToken = tokenRepos
      .find(_._1.exists(repo => req.uri.toString.contains(repo.toPath)))
      .map(_._2.token)
  } yield maybeToken match {
    case Some(token) => req.putHeaders(Authorization(Token(AuthScheme.Bearer, token)))
    case None        => req
  }

  override def authenticateGit(uri: Uri): F[Uri] = for {
    tokenRepos <- tokenRepos
    tokenMaybe = tokenRepos
      .find(_._1.exists(repo => uri.toString.contains(repo.toPath)))
      .map(_._2.token)
  } yield util.uri.withUserInfo.replace(UserInfo("scala-steward", tokenMaybe))(uri)

  private def tokenRepos = for {
    jwt <- createJWT(tokenTtl)
    installations <- installations(jwt)
    tokens <- installations.traverse(installation => accessToken(jwt, installation.id))
    tokenRepos <- tokens.traverse(token => repositories(token.token).map(_ -> token))
  } yield tokenRepos

  override def accessibleRepos: F[List[Repo]] = for {
    jwt <- createJWT(tokenTtl)
    installations <- installations(jwt)
    tokens <- installations.traverse(installation => accessToken(jwt, installation.id))
    repos <- tokens.flatTraverse(token => repositories(token.token))
  } yield repos

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-repositories-accessible-to-the-app-installation]]
    */
  private[github] def repositories(token: String): F[List[Repo]] =
    client
      .getAll[RepositoriesOut](
        (apiUri / "installation" / "repositories").withQueryParam("per_page", 100),
        req => F.point(req.putHeaders(Header.Raw(ci"Authorization", s"token $token")))
      )
      .compile
      .toList
      .flatMap(values =>
        values
          .flatMap(_.repositories)
          .flatTraverse(_.full_name.split('/') match {
            case Array(owner, name) => F.pure(List(Repo(owner, name)))
            case _                  => logger.error(s"invalid repo ").as(List.empty[Repo])
          })
      )

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#list-installations-for-the-authenticated-app]]
    */
  private[github] def installations(jwt: String): F[List[InstallationOut]] =
    client
      .getAll[List[InstallationOut]](
        (apiUri / "app" / "installations").withQueryParam("per_page", 100),
        addHeaders(jwt)
      )
      .compile
      .foldMonoid

  /** [[https://docs.github.com/en/free-pro-team@latest/rest/reference/apps#create-an-installation-access-token-for-an-app]]
    */
  private[github] def accessToken(jwt: String, installationId: Long): F[TokenOut] =
    client.post(
      apiUri / "app" / "installations" / installationId.toString / "access_tokens",
      addHeaders(jwt)
    )

  /** [[https://docs.github.com/en/free-pro-team@latest/developers/apps/authenticating-with-github-apps#authenticating-as-a-github-app]]
    */
  private[github] def createJWT(ttl: FiniteDuration): F[String] =
    F.delay(System.currentTimeMillis()).flatMap(createJWT(ttl, _))

  private[github] def createJWT(ttl: FiniteDuration, nowMillis: Long): F[String] =
    F.delay {
      Security.addProvider(new BouncyCastleProvider())
      val ttlMillis = ttl.toMillis
      val now = new Date(nowMillis)
      val signingKey = readPrivateKey(appKeyFile)
      val builder = Jwts
        .builder()
        .issuedAt(now)
        .issuer(appId.toString)
        .signWith(signingKey, Jwts.SIG.RS256)
      if (ttlMillis > 0) {
        val expMillis = nowMillis + ttlMillis
        val exp = new Date(expMillis)
        builder.expiration(exp)
        ()
      }
      builder.compact()
    }

  private def addHeaders(jwt: String): client.ModReq =
    req => F.point(req.putHeaders(Authorization(Token(AuthScheme.Bearer, jwt))))
}
