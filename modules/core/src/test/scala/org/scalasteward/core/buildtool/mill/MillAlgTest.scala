package org.scalasteward.core.buildtool.mill

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.mill.MillAlg.extractDeps
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.millAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class MillAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.toPath
    val predef = s"$repoDir/scala-steward.sc"
    val millCmd = List(
      "firejail",
      "--quiet",
      s"--whitelist=$repoDir",
      "--env=VAR1=val1",
      "--env=VAR2=val2",
      "mill",
      "-i",
      "-p",
      predef,
      "show",
      extractDeps
    )
    val initial = MockState.empty.copy(commandOutputs = Map(millCmd -> List("""{"modules":[]}""")))
    val state = millAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$repoDir/.mill-version"),
        Cmd("write", predef),
        Cmd(repoDir.toString :: millCmd),
        Cmd("rm", "-rf", predef)
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
