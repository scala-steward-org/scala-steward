package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.edit.update.data.FilePosition
import org.scalasteward.core.edit.update.data.ModulePosition.SbtModuleId

class ModuleScannerTest extends FunSuite {
  test("sbt module") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s""""${d.groupId}" %% "${d.artifactId.name}" % "${d.version}""""
    val obtained = ModuleScanner.findModulePositions(d, content)
    val expected = List(
      SbtModuleId(
        FilePosition(1, d.groupId.value),
        FilePosition(20, d.artifactId.name),
        FilePosition(33, s"\"${d.version}\"")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion"""
    val obtained = ModuleScanner.findModulePositions(d, content)
    val expected = List(
      SbtModuleId(
        FilePosition(1, d.groupId.value),
        FilePosition(20, d.artifactId.name),
        FilePosition(33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }
}
