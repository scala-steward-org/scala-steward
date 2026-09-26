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

package org.scalasteward.core.buildtool.gradle

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*

class gradleParserTest extends FunSuite {
  test("parseDependenciesAndPlugins: valid input") {
    val input =
      """|[versions]
         |groovy = "3.0.5"
         |checkstyle = "8.37"
         |
         |[libraries]
         |groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
         |groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
         |groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
         |commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }
         |tomlj = { group = "org.tomlj", name = "tomlj", version = "1.1.1" }
         |
         |[bundles]
         |groovy = ["groovy-core", "groovy-json", "groovy-nio"]
         |
         |[plugins]
         |versions = { id = "com.github.ben-manes.versions", version = "0.45.0" }
         |""".stripMargin
    val obtained = gradleParser.parseDependenciesAndPlugins(input)
    val expected = (
      List(
        "org.codehaus.groovy".g % "groovy".a % "3.0.5",
        "org.codehaus.groovy".g % "groovy-json".a % "3.0.5",
        "org.codehaus.groovy".g % "groovy-nio".a % "3.0.5",
        "org.tomlj".g % "tomlj".a % "1.1.1"
      ),
      List(
        "com.github.ben-manes.versions".g % "com.github.ben-manes.versions.gradle.plugin".a % "0.45.0"
      )
    )
    assertEquals(obtained, expected)
  }

  test("parseDependenciesAndPlugins: empty input") {
    val obtained = gradleParser.parseDependenciesAndPlugins("")
    assertEquals(obtained, (List.empty, List.empty))
  }

  test("parseDependenciesAndPlugins: malformed input") {
    val input =
      """|versions]
         |groovy = "3.0.5"
         |[libraries]
         |groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy"
         |foo = { module = "bar:qux:foo", version = "1" }
         |[plugins]
         |foo = ""
         |""".stripMargin
    val obtained = gradleParser.parseDependenciesAndPlugins(input)
    assertEquals(obtained, (List.empty, List.empty))
  }
}
