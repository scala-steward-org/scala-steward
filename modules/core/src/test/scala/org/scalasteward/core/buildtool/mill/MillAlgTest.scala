package org.scalasteward.core.buildtool.mill

import munit.FunSuite
import org.scalasteward.core.buildtool.mill.MillAlg.extractDeps
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockContext.context.millAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class MillAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("lihaoyi", "fastparse")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.show
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
      commands = Vector(
        List("write", predef),
        repoDir.toString :: millCmd,
        List("rm", "-rf", predef)
      )
    )
    assertEquals(state, expected)
  }
}
