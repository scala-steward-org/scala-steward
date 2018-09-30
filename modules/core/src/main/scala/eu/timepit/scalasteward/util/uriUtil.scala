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

package eu.timepit.scalasteward.util

import cats.ApplicativeError
import cats.implicits._
import eu.timepit.scalasteward.github.data.AuthenticatedUser
import io.circe.Decoder
import org.http4s.Uri

object uriUtil {
  implicit val uriDecoder: Decoder[Uri] = {
    type Result[A] = Either[Throwable, A]
    Decoder[String].emap(s => fromString[Result](s).leftMap(_.getMessage))
  }

  def fromString[F[_]](s: String)(implicit F: ApplicativeError[F, Throwable]): F[Uri] =
    F.fromEither(Uri.fromString(s))

  def withUserInfo(uri: Uri, user: AuthenticatedUser): Uri =
    uri.authority.fold(uri) { auth =>
      uri.copy(authority = Some(auth.copy(userInfo = Some(user.login + ":" + user.accessToken))))
    }
}
