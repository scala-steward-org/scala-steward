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

package org.scalasteward.core.git

import io.circe.Decoder

final case class Author(name: String, email: String, signingKey: Option[String] = None) {
  def show: String = s"$name <$email>"
}

object Author {
  private val regex = """(.*)\s+<(.*)>\s*""".r

  def parse(s: String): Either[String, Author] =
    s match {
      case regex(name, email) => Right(Author(name.trim, email.trim))
      case _ => Left(s"Could not parse '$s' as Author. Expected format is: $$name <$$email>")
    }

  implicit val authorDecoder: Decoder[Author] =
    Decoder[String].emap(parse)
}
