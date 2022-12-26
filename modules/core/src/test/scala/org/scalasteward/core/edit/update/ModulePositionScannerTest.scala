package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
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
}
