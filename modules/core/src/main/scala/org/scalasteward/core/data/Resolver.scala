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

package org.scalasteward.core.data

import cats.Order
import io.circe.Codec
import io.circe.generic.semiauto.*
import org.scalasteward.core.data.Resolver.*

sealed trait Resolver extends Product with Serializable {
  val path: String = {
    val url = this match {
      case MavenRepository(_, location, _, _) => location
      case IvyRepository(_, pattern, _, _)    => pattern.takeWhile(!Set('[', '(')(_))
    }
    url.replace(":", "")
  }
}

object Resolver {
  final case class Credentials(user: String, pass: String)
  object Credentials {
    implicit val credentialsCodec: Codec[Credentials] =
      deriveCodec
  }
  final case class Header(key: String, value: String)
  object Header {
    implicit val headerCodec: Codec[Header] =
      deriveCodec
  }
  final case class MavenRepository(
      name: String,
      location: String,
      credentials: Option[Credentials],
      headers: Option[List[Header]]
  ) extends Resolver
  final case class IvyRepository(
      name: String,
      pattern: String,
      credentials: Option[Credentials],
      headers: Option[List[Header]]
  ) extends Resolver

  val mavenCentral: MavenRepository =
    MavenRepository("public", "https://repo1.maven.org/maven2/", None, None)

  implicit val resolverCodec: Codec[Resolver] =
    deriveCodec

  implicit val resolverOrder: Order[Resolver] =
    Order.by {
      case MavenRepository(name, location, _, _) => (1, name, location)
      case IvyRepository(name, pattern, _, _)    => (2, name, pattern)
    }
}
