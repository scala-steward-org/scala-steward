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

package org.scalasteward.core.forge.github

import better.files.File
import cats.effect.Sync
import cats.implicits._
import io.jsonwebtoken.Jwts
import java.io.FileReader
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey, Security}
import java.util.Date
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader
import scala.concurrent.duration.FiniteDuration
import scala.util.Using

trait GitHubAuthAlg[F[_]] {

  /** [[https://docs.github.com/en/free-pro-team@latest/developers/apps/authenticating-with-github-apps#authenticating-as-a-github-app]]
    */
  def createJWT(app: GitHubApp, ttl: FiniteDuration): F[String]

  def createJWT(app: GitHubApp, ttl: FiniteDuration, nowMillis: Long): F[String]

}

object GitHubAuthAlg {
  def create[F[_]](implicit F: Sync[F]): GitHubAuthAlg[F] =
    new GitHubAuthAlg[F] {
      private[this] def parsePEMFile(pemFile: File): Array[Byte] =
        Using.resource(new PemReader(new FileReader(pemFile.toJava))) { reader =>
          reader.readPemObject().getContent
        }

      private[this] def getPrivateKey(keyBytes: Array[Byte]): PrivateKey = {
        val kf = KeyFactory.getInstance("RSA")
        val keySpec = new PKCS8EncodedKeySpec(keyBytes)
        kf.generatePrivate(keySpec)
      }

      private[this] def readPrivateKey(file: File): PrivateKey = {
        val bytes = parsePEMFile(file)
        getPrivateKey(bytes)
      }

      /** [[https://docs.github.com/en/free-pro-team@latest/developers/apps/authenticating-with-github-apps#authenticating-as-a-github-app]]
        */
      def createJWT(app: GitHubApp, ttl: FiniteDuration): F[String] =
        F.delay(System.currentTimeMillis()).flatMap(createJWT(app, ttl, _))

      def createJWT(app: GitHubApp, ttl: FiniteDuration, nowMillis: Long): F[String] =
        F.delay {
          Security.addProvider(new BouncyCastleProvider())
          val ttlMillis = ttl.toMillis
          val now = new Date(nowMillis)
          val signingKey = readPrivateKey(app.keyFile)
          val builder = Jwts
            .builder()
            .issuedAt(now)
            .issuer(app.id.toString)
            .signWith(signingKey, Jwts.SIG.RS256)
          if (ttlMillis > 0) {
            val expMillis = nowMillis + ttlMillis
            val exp = new Date(expMillis)
            builder.expiration(exp)
          }
          builder.compact()
        }
    }
}
