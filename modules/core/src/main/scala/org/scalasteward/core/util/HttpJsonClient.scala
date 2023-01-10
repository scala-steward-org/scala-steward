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

package org.scalasteward.core.util

import cats.effect.Concurrent
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import org.http4s.Method.{GET, PATCH, POST, PUT}
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.headers.{Accept, Link, MediaRangeAndQValue}
import scala.util.control.NoStackTrace

final class HttpJsonClient[F[_]](implicit
    client: Client[F],
    F: Concurrent[F]
) {
  type ModReq = Request[F] => F[Request[F]]

  def get[A: Decoder](uri: Uri, modify: ModReq): F[A] =
    request[A](GET, uri, modify)

  def getAll[A: Decoder](uri: Uri, modify: ModReq): F[List[A]] =
    all[A](GET, uri, modify, Nil)

  private def all[A: Decoder](
      method: Method,
      uri: Uri,
      modify: ModReq,
      xs: List[A]
  ): F[List[A]] =
    requestWithHeaders[A](method, uri, modify)(jsonOf).flatMap { case (a, headers) =>
      headers.get[Link].flatMap(_.values.find(_.rel.contains("next"))) match {
        case Some(linkValue) => all(method, linkValue.uri, modify, a :: xs)
        case None            => F.pure(a :: xs)
      }
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
    post[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[B])))

  def putWithBody[A: Decoder, B: Encoder](uri: Uri, body: B, modify: ModReq): F[A] =
    put[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[B])))

  def patchWithBody[A: Decoder, B: Encoder](uri: Uri, body: B, modify: ModReq): F[A] =
    patch[A](uri, modify.compose(_.withEntity(body)(jsonEncoderOf[B])))

  private def request[A: Decoder](method: Method, uri: Uri, modify: ModReq): F[A] =
    requestWithHeaders[A](method, uri, modify)(jsonOf).map { case (a, _) => a }

  private def request_(method: Method, uri: Uri, modify: ModReq): F[Unit] =
    requestWithHeaders[Unit](method, uri, modify).void

  // adapted from https://github.com/http4s/http4s/blob/c89ebc2d844c5c93dcc1307e5b9361a2c38bfd00/client/shared/src/main/scala/org/http4s/client/DefaultClient.scala#L91-L105
  private def requestWithHeaders[A](method: Method, uri: Uri, modify: ModReq)(implicit
      d: EntityDecoder[F, A]
  ): F[(A, Headers)] =
    modify(Request[F](method, uri)).flatMap { req =>
      val r = Nel
        .fromList(d.consumes.toList.map(MediaRangeAndQValue(_)))
        .fold(req)(m => req.addHeader(Accept(m)))

      client.run(r).use {
        case Successful(resp) =>
          d.decode(resp, strict = false).value.flatMap {
            case Right(a)      => F.pure((a, resp.headers))
            case Left(failure) => F.raiseError(DecodeFailureWithContext(uri, method, failure))
          }
        case resp => toUnexpectedResponse(uri, method, resp).flatMap(_.raiseError)
      }
    }

  private def toUnexpectedResponse(
      uri: Uri,
      method: Method,
      response: Response[F]
  ): F[Throwable] = {
    val body = response.body.through(fs2.text.utf8.decode).compile.string
    body.map(UnexpectedResponse(uri, method, response.headers, response.status, _))
  }
}

/** A wrapper of a `org.http4s.DecodeFailure` that contains additional context to ease debugging. */
final case class DecodeFailureWithContext(
    uri: Uri,
    method: Method,
    underlying: DecodeFailure
) extends DecodeFailure
    with NoStackTrace {
  override val message: String =
    s"""|uri: $uri
        |method: $method
        |message: ${underlying.message}""".stripMargin

  override def cause: Option[Throwable] =
    underlying.some

  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    underlying.toHttpResponse(httpVersion)
}

/** Like `org.http4s.client.UnexpectedStatus` but contains additional context to ease debugging. */
final case class UnexpectedResponse(
    uri: Uri,
    method: Method,
    headers: Headers,
    status: Status,
    body: String
) extends RuntimeException
    with NoStackTrace {
  override def getMessage: String =
    s"""|uri: $uri
        |method: $method
        |status: $status
        |headers:
        |${headers.headers.map(h => s"  ${h.show}").mkString("\n")}
        |body: $body""".stripMargin
}
