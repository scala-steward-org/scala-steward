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

package org.scalasteward.core.forge.data

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import scala.io.Source

class RepoOutTest extends FunSuite {
  private val parent =
    RepoOut(
      "base.g8",
      UserOut("ChristopherDavenport"),
      None,
      uri"https://github.com/ChristopherDavenport/base.g8.git",
      Branch("master")
    )

  private val fork =
    RepoOut(
      "base.g8-1",
      UserOut("scala-steward"),
      Some(parent),
      uri"https://github.com/scala-steward/base.g8-1.git",
      Branch("master")
    )

  test("decode") {
    val input = Source.fromResource("create-fork.json").mkString
    assertEquals(parser.decode[RepoOut](input), Right(fork))
  }

  test("parentOrRaise") {
    assertEquals(fork.parentOrRaise[IO].unsafeRunSync(), parent)
  }

  test("repo") {
    assertEquals(fork.repo, Repo("scala-steward", "base.g8-1"))
  }
}
