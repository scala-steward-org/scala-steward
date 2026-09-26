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
import org.scalasteward.core.git.{Branch, Sha1}
import scala.io.Source

class BranchOutTest extends FunSuite {
  test("decode") {
    val input = Source.fromResource("get-branch.json").mkString
    val expected = Right(
      BranchOut(
        Branch("master"),
        CommitOut(Sha1.unsafeFrom("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d"))
      )
    )
    assertEquals(parser.decode[BranchOut](input), expected)
  }
}
