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
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}

class MillPluginParserTest extends FunSuite {

  val supportedVersionExamples = Map("0.9" -> "0.9.12", "0.10" -> "0.10.7")

  supportedVersionExamples.foreach { case (versionSuffix, millVersion) =>
    test(s"basic-${versionSuffix}") {
      val fileContent =
        """|import $ivy.`com.goyeau::mill-scalafix::0.2.10`
           |import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`""".stripMargin

      val expected = List(
        Dependency(
          GroupId("com.goyeau"),
          ArtifactId("mill-scalafix", s"mill-scalafix_mill${versionSuffix}_2.13"),
          Version("0.2.10")
        ),
        Dependency(
          GroupId("de.tototec"),
          ArtifactId(
            "de.tobiasroeser.mill.integrationtest",
            s"de.tobiasroeser.mill.integrationtest_mill${versionSuffix}_2.13"
          ),
          Version("0.6.1")
        )
      )

      val parsed = parser.parseMillPluginDeps(fileContent, Version(millVersion))

      assertEquals(parsed, expected)
    }
  }

}
