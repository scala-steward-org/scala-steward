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

package org.scalasteward.core.util

import cats.effect.Sync
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import org.http4s.Method.{GET, PATCH, POST, PUT}
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.headers.Link
import scala.util.control.NoStackTrace

final class HttpJsonClient[F[_]: Sync](implicit
    client: Client[F]
) {
  type ModReq = Request[F] => F[Request[F]]

  def get[A: Decoder](uri: Uri, modify: ModReq): F[A] =
    request[A](GET, uri, modify)

  def getAll[A: Decoder](uri: Uri, modify: ModReq): F[List[A]] =
    all[A](GET, uri, modify, Nil)

  private[this] def all[A: Decoder](
      method: Method,
      uri: Uri,
      modify: ModReq,
      xs: List[A]
  ): F[List[A]] =
    requestWithHeader[A](method, uri, modify, Link).flatMap {
      case (res, Some(linkHeader)) =>
        linkHeader.values.find(_.rel === Option("next")) match {
          case Some(link) =>
            all(method, link.uri, modify, res :: xs)
          case None =>
            (res :: xs).pure[F]
        }
      case (res, None) =>
        (res :: xs).pure[F]
    }

  def post[A: Decoder](uri: Uri, modify: ModReq): F[A] =
    request[A](POST, uri, modify)

  def post_(uri: Uri, modify: ModReq): F[Unit] =
    request_(POST, uri, modify)

  def put[A: Decoder](uri: Uri, modify: ModReq): F[A] =
    request[A](PUT, uri, modify)

  def patch[A: Decoder](uri: Uri, modify: ModReq): F[A] =
    request[A](PATCH, uri, modify)

  def postWithBody[A: Decoder, B: Encoder](uri: Uri, body: B, modify: ModReq): F[A] =
    post[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[F, B])))

  def putWithBody[A: Decoder, B: Encoder](uri: Uri, body: B, modify: ModReq): F[A] =
    put[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[F, B])))

  def patchWithBody[A: Decoder, B: Encoder](uri: Uri, body: B, modify: ModReq): F[A] =
    patch[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[F, B])))

  private[this] def requestWithHeader[A: Decoder](
      method: Method,
      uri: Uri,
      modify: ModReq,
      header: HeaderKey.Extractable
  ): F[(A, Option[header.HeaderT])] = {
    val decoder = jsonOf[F, A].transform(_.leftMap(failure => JsonParseError(uri, method, failure)))
    modify(Request[F](method, uri))
      .flatMap(client.run(_).use {
        case Successful(resp) =>
          decoder.decode(resp, strict = false).rethrowT.map {
            _ -> resp.headers.get(header)
          }
        case resp =>
          toUnexpectedResponse(uri, method, resp).flatMap(_.raiseError)
      })
  }

  private def request[A: Decoder](method: Method, uri: Uri, modify: ModReq): F[A] =
    client.expectOr[A](modify(Request[F](method, uri)))(resp =>
      toUnexpectedResponse(uri, method, resp)
    )(jsonOf[F, A].transform(_.leftMap(failure => JsonParseError(uri, method, failure))))

  private def request_(method: Method, uri: Uri, modify: ModReq): F[Unit] =
    client.expectOr[Unit](modify(Request[F](method, uri)))(resp =>
      toUnexpectedResponse(uri, method, resp)
    )

  private def toUnexpectedResponse(
      uri: Uri,
      method: Method,
      response: Response[F]
  ): F[Throwable] = {
    val body = response.body.through(fs2.text.utf8Decode).compile.string
    body.map(UnexpectedResponse(uri, method, response.headers, response.status, _))
  }
}

final case class JsonParseError(
    uri: Uri,
    method: Method,
    underlying: DecodeFailure
) extends DecodeFailure {
  val message = s"uri: $uri\nmethod: $method\nmessage: ${underlying.message}"
  override def cause: Option[Throwable] = underlying.some
  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    underlying.toHttpResponse(httpVersion)
}

final case class UnexpectedResponse(
    uri: Uri,
    method: Method,
    headers: Headers,
    status: Status,
    body: String
) extends RuntimeException
    with NoStackTrace {
  override def getMessage: String =
    s"uri: $uri\nmethod: $method\nstatus: $status\nheaders: $headers\nbody: $body"
}
