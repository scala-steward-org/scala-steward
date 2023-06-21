package org.scalasteward.core.buildtool.mill

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.mill.MillAlg.extractDeps
import org.scalasteward.core.data.{Repo, Version}
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

class MillAlgTest extends FunSuite {
  test("getDependencies, version < 0.11") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val predef = s"$repoDir/scala-steward.sc"
    val millCmd = Cmd.execSandboxed(repoDir, "mill", "-i", "-p", predef, "show", extractDeps)
    val initial =
      MockState.empty.copy(commandOutputs = Map(millCmd -> Right(List("""{"modules":[]}"""))))
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$repoDir/.mill-version"),
        Cmd("read", s"$repoDir/.config/mill-version"),
        Cmd("write", predef),
        millCmd,
        Cmd("rm", "-rf", predef)
      )
    )
    assertEquals(state, expected)
  }

  test("getDependencies, version >= 0.11") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val buildRootDir = workspaceAlg.buildRootDir(buildRoot).unsafeRunSync()
    val millCmd = Cmd.execSandboxed(
      repoDir,
      "mill",
      "-i",
      "--import",
      "ivy:org.scala-steward::scala-steward-mill-plugin::0.18.0",
      "show",
      extractDeps
    )
    val initial =
      MockState.empty
        .copy(
          commandOutputs = Map(millCmd -> Right(List("""{"modules":[]}""")))
        )
        .addFiles(buildRootDir / ".mill-version" -> "0.11.0")
        .unsafeRunSync()
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$repoDir/.mill-version"),
        Cmd("read", s"$repoDir/.config/mill-version"),
        millCmd,
        Cmd("read", s"$repoDir/build.sc")
      )
    )
    assertEquals(state, expected)
  }

  test("predef-content") {
    assert(MillAlg.content(None).contains("_mill$MILL_BIN_PLATFORM"))
    assert(MillAlg.content(Some(Version("0.6.1"))).contains("_mill0.6"))
    assert(MillAlg.content(Some(Version("0.7.0"))).contains("_mill0.7"))
    assert(MillAlg.content(Some(Version("0.8.0"))).contains("_mill0.7"))
    assert(MillAlg.content(Some(Version("0.9.14"))).contains("_mill0.9"))
    assert(MillAlg.content(Some(Version("0.10.0"))).contains("_mill$MILL_BIN_PLATFORM"))
  }

  test("mill version") {
    assert(MillAlg.millMinVersion(None).isEmpty)
    assert(MillAlg.millMinVersion(Some(Version("0.6.1"))).contains("6"))
    assert(MillAlg.millMinVersion(Some(Version("0.7.0"))).contains("7"))
    assert(MillAlg.millMinVersion(Some(Version("0.8.0"))).contains("8"))
    assert(MillAlg.millMinVersion(Some(Version("0.9.14"))).contains("9"))
    assert(MillAlg.millMinVersion(Some(Version("0.10.0"))).contains("10"))
    assert(MillAlg.millMinVersion(Some(Version("0.11.0"))).contains("11"))
  }
}
