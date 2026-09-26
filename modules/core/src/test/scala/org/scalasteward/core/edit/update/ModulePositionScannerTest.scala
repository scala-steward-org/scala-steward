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

package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.edit.update.data.{ModulePosition, Substring}
import org.scalasteward.core.io.FileData

class ModulePositionScannerTest extends FunSuite {
  test("sbt module") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData("build.sbt", s""""${d.groupId}" %% "${d.artifactId.name}" % "${d.version}"""")
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 20, d.artifactId.name),
        Substring.Position(fd.path, 33, s"\"${d.version}\"")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData("build.sbt", s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion""")
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 20, d.artifactId.name),
        Substring.Position(fd.path, 33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val and comment") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData(
      "build.sbt",
      s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion // this is a comment"""
    )
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 20, d.artifactId.name),
        Substring.Position(fd.path, 33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val and comment with /* */") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData(
      "build.sbt",
      s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion /* this is a comment */"""
    )
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 20, d.artifactId.name),
        Substring.Position(fd.path, 33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val and end space") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val fd = FileData("build.sbt", s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion """)
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 20, d.artifactId.name),
        Substring.Position(fd.path, 33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module where the artifactId is also part of the groupId") {
    val d = "com.typesafe.play".g % "play".a % "2.9.0"
    val fd = FileData("build.sbt", s""""com.typesafe.play" %% "play" % "2.9.0"""")
    val obtained = ModulePositionScanner.findPositions(d, fd)
    val expected = List(
      ModulePosition(
        Substring.Position(fd.path, 1, d.groupId.value),
        Substring.Position(fd.path, 24, d.artifactId.name),
        Substring.Position(fd.path, 32, s"\"${d.version}\"")
      )
    )
    assertEquals(obtained, expected)
  }
}
