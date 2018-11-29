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

package org.scalasteward.core.util

import cats.implicits._
import io.circe.Decoder
import org.http4s.Uri

object uri {
  implicit val uriDecoder: Decoder[Uri] =
    Decoder[String].emap(s => fromString[Either[Throwable, ?]](s).leftMap(_.getMessage))

  def fromString[F[_]](s: String)(implicit F: ApplicativeThrowable[F]): F[Uri] =
    F.fromEither(Uri.fromString(s))

  def withUserInfo(uri: Uri, userInfo: String): Uri =
    uri.authority.fold(uri) { auth =>
      uri.copy(authority = Some(auth.copy(userInfo = Some(userInfo))))
    }
}
