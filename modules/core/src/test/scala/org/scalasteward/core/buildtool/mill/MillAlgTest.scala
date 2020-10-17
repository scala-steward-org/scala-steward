package org.scalasteward.core.buildtool.mill

import org.scalasteward.core.buildtool.mill.MillAlg.extractDeps
import org.scalasteward.core.mock.MockContext.{config, millAlg}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MillAlgTest extends AnyFunSuite with Matchers {
  test("getDependencies") {
    val repo = Repo("lihaoyi", "fastparse")
    val repoDir = config.workspace / repo.show
    val predef = s"$repoDir/scala-steward.sc"
    val millCmd =
      List("firejail", s"--whitelist=$repoDir", "mill", "-i", "-p", predef, "show", extractDeps)
    val initial = MockState.empty.copy(commandOutputs = Map(millCmd -> List("""{"modules":[]}""")))
    val state = millAlg.getDependencies(repo).runS(initial).unsafeRunSync()
    state shouldBe initial.copy(
      commands = Vector(
        List("write", predef),
        List("VAR1=val1", "VAR2=val2", repoDir.toString) ++ millCmd,
        List("rm", "-rf", predef)
      )
    )
  }
}
