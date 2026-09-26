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

import io.circe.parser
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.forge.data.PullRequestState.Open
import scala.io.Source

class PullRequestOutTest extends FunSuite {
  test("decode") {
    val expected =
      List(
        PullRequestOut(
          uri"https://github.com/octocat/Hello-World/pull/1347",
          Open,
          PullRequestNumber(1347),
          "new-feature"
        )
      )

    val input = Source.fromResource("list-pull-requests.json").mkString
    assertEquals(parser.decode[List[PullRequestOut]](input), Right(expected))
  }
}
