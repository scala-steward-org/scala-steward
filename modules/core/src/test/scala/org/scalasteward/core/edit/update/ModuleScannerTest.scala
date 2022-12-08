package org.scalasteward.core.edit.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.edit.update.data.SubstringPosition
import org.scalasteward.core.edit.update.data.ModulePosition.SbtDependency

class ModuleScannerTest extends FunSuite {
  test("sbt module") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s""""${d.groupId}" %% "${d.artifactId.name}" % "${d.version}""""
    val obtained = ModuleScanner.findPositions(d, content)
    val expected = List(
      SbtDependency(
        SubstringPosition(1, d.groupId.value),
        SubstringPosition(20, d.artifactId.name),
        SubstringPosition(33, s"\"${d.version}\"")
      )
    )
    assertEquals(obtained, expected)
  }

  test("sbt module with version val") {
    val d = "org.typelevel".g % "cats-core".a % "2.9.0"
    val content = s""""${d.groupId}" %% "${d.artifactId.name}" % catsVersion"""
    val obtained = ModuleScanner.findPositions(d, content)
    val expected = List(
      SbtDependency(
        SubstringPosition(1, d.groupId.value),
        SubstringPosition(20, d.artifactId.name),
        SubstringPosition(33, "catsVersion")
      )
    )
    assertEquals(obtained, expected)
  }
}
