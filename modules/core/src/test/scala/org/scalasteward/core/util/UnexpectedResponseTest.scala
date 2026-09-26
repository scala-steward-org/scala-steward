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

import munit.FunSuite
import org.http4s.*
import org.typelevel.ci.CIString

class UnexpectedResponseTest extends FunSuite {
  test("getMessage") {
    val unexpected = UnexpectedResponse(
      Uri.unsafeFromString("https://api.github.com/repos/foo/bar/pulls"),
      Method.POST,
      Headers(
        Header.Raw(CIString("access-control-allow-origin"), "*"),
        Header.Raw(CIString("content-type"), "application/json; charset=utf-8")
      ),
      Status.Forbidden,
      """{ message: "nope" }"""
    )
    val expected =
      """|uri: https://api.github.com/repos/foo/bar/pulls
         |method: POST
         |status: 403 Forbidden
         |headers:
         |  access-control-allow-origin: *
         |  content-type: application/json; charset=utf-8
         |body: { message: "nope" }""".stripMargin
    assertEquals(unexpected.getMessage, expected)
  }
}
