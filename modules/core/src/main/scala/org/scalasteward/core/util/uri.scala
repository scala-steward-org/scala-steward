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

package org.scalasteward.core.util

import cats.syntax.all.*
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import monocle.Optional
import org.http4s.Uri
import org.http4s.Uri.{Authority, Host, Scheme, UserInfo}

object uri {
  implicit val uriDecoder: Decoder[Uri] =
    Decoder[String].emap(s => Uri.fromString(s).leftMap(_.getMessage))

  implicit val uriEncoder: Encoder[Uri] =
    Encoder[String].contramap[Uri](_.renderString)

  implicit val uriKeyDecoder: KeyDecoder[Uri] =
    KeyDecoder.instance(Uri.fromString(_).toOption)

  implicit val uriKeyEncoder: KeyEncoder[Uri] =
    KeyEncoder.instance(_.renderString)

  private val withAuthority: Optional[Uri, Authority] =
    Optional[Uri, Authority](_.authority)(authority => _.copy(authority = Some(authority)))

  private val authorityWithUserInfo: Optional[Authority, UserInfo] =
    Optional[Authority, UserInfo](_.userInfo)(userInfo => _.copy(userInfo = Some(userInfo)))

  val withUserInfo: Optional[Uri, UserInfo] =
    authorityWithUserInfo.compose(withAuthority)

  /** Parses the given `String` into a `Uri` and overrides its `Uri.Scheme` according to
    * `updateSchemeMaybe`. Otherwise, the `Uri.Scheme` remains unchanged after parsing.
    */
  def fromStringWithScheme(
      s: String
  )(updateSchemeMaybe: (Scheme, Host) => Option[Scheme]): Option[Uri] =
    Uri
      .fromString(s)
      .toOption
      .filter(_.scheme.isDefined)
      .mapOrKeep { case uri @ UriWithSchemeAndHost(scheme, host) =>
        val newSchemeMaybe = updateSchemeMaybe(scheme, host)

        newSchemeMaybe.fold(uri)(_ => uri.copy(scheme = newSchemeMaybe))
      }

  val httpSchemes: Set[Scheme] =
    Set(Scheme.https, Scheme.http)

  private object UriWithSchemeAndHost {
    def unapply(uri: Uri): Option[(Scheme, Host)] = uri match {
      case Uri(Some(scheme), Some(authority), _, _, _) =>
        (scheme -> authority.host).some
      case _ =>
        None
    }
  }
}
