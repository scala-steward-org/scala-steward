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

package org.scalasteward.core.buildtool.mill

import munit.FunSuite

class MillVersionParserTest extends FunSuite {
  val data = Seq(
    "" -> None,
    "0.6.0" -> Some("0.6.0"),
    "0.9.6-16-a5da34" -> Some("0.9.6-16-a5da34"),
    """0.6.0
      |""".stripMargin -> Some("0.6.0"),
    "\r\n0.6.0\r\n".stripMargin -> Some("0.6.0"),
    " 0.6.0 " -> Some("0.6.0")
  )

  for {
    (versionFileContent, expected) <- data
  } test(s"parse version from .mill-version file with content '$versionFileContent'") {
    val parsed = parser.parseMillVersion(versionFileContent).map(_.value)
    assertEquals(parsed, expected)
  }

  test(s"parse version from build.mill file") {
    val buildMillFileContent = """
                                 |//| mill-version: 1.0.5
                                 |""".stripMargin
    val parsed = parser.parseBuildFileMillVersion(buildMillFileContent).map(_.value)
    assertEquals(parsed, Some("1.0.5"))
  }

  test(s"parse quoted version from build.mill file") {
    val buildMillFileContent = """
                                 |//| mill-version: "1.0.5"
                                 |""".stripMargin
    val parsed = parser.parseBuildFileMillVersion(buildMillFileContent).map(_.value)
    assertEquals(parsed, Some("1.0.5"))
  }

  test(s"parse single quoted version from build.mill file") {
    val buildMillFileContent = """
                                 |//| mill-version: '1.0.5'
                                 |""".stripMargin
    val parsed = parser.parseBuildFileMillVersion(buildMillFileContent).map(_.value)
    assertEquals(parsed, Some("1.0.5"))
  }
}
