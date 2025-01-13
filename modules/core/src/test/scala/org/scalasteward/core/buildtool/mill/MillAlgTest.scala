package org.scalasteward.core.buildtool.mill

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.mill.MillAlg.extractDeps
import org.scalasteward.core.data.{Repo, Version}
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEffOps, MockState}

class MillAlgTest extends FunSuite {
  test("getDependencies, version < 0.11") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val buildRootDir = workspaceAlg.buildRootDir(buildRoot).unsafeRunSync()
    val predef = s"$buildRootDir/scala-steward.sc"
    val millCmd = Cmd.execSandboxed(buildRootDir, "mill", "-i", "-p", predef, "show", extractDeps)
    val initial =
      MockState.empty.copy(commandOutputs = Map(millCmd -> Right(List("""{"modules":[]}"""))))
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$buildRootDir/.mill-version"),
        Cmd("read", s"$buildRootDir/.config/mill-version"),
        Cmd("write", predef),
        millCmd,
        Cmd("rm", "-rf", predef)
      )
    )
    assertEquals(state, expected)
  }

  test("getDependencies, 0.11 <= version < 0.12") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val buildRootDir = workspaceAlg.buildRootDir(buildRoot).unsafeRunSync()
    val millCmd = Cmd.execSandboxed(
      buildRootDir,
      "mill",
      "--no-server",
      "--disable-ticker",
      "--import",
      "ivy:org.scala-steward::scala-steward-mill-plugin::0.18.0",
      "show",
      extractDeps
    )
    val initial = MockState.empty
      .copy(commandOutputs = Map(millCmd -> Right(List("""{"modules":[]}"""))))
      .addFiles(buildRootDir / ".mill-version" -> "0.11.0", buildRootDir / "build.sc" -> "")
      .unsafeRunSync()
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$buildRootDir/.mill-version"),
        millCmd,
        Cmd("test", "-f", s"$buildRootDir/build.sc"),
        Cmd("read", s"$buildRootDir/build.sc")
      )
    )
    assertEquals(state, expected)
  }

  test("getDependencies, 0.12 <= version") {
    val repo = Repo("mill-alg", "test-3")
    val buildRoot = BuildRoot(repo, ".")
    val buildRootDir = workspaceAlg.buildRootDir(buildRoot).unsafeRunSync()
    val millCmd = Cmd.execSandboxed(
      buildRootDir,
      "mill",
      "--no-server",
      "--ticker",
      "false",
      "--import",
      "ivy:org.scala-steward::scala-steward-mill-plugin::0.18.0",
      "show",
      extractDeps
    )
    val initial = MockState.empty
      .copy(commandOutputs = Map(millCmd -> Right(List("""{"modules":[]}"""))))
      .addFiles(buildRootDir / ".mill-version" -> "0.12.5", buildRootDir / "build.sc" -> "")
      .unsafeRunSync()
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$buildRootDir/.mill-version"),
        millCmd,
        Cmd("test", "-f", s"$buildRootDir/build.sc"),
        Cmd("read", s"$buildRootDir/build.sc")
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
}
