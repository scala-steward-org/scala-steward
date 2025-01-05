package org.scalasteward.core.repoconfig

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.validateRepoConfigContext.validateRepoConfigAlg
import org.scalasteward.core.mock.{MockEffOps, MockState}

class ValidateRepoConfigAlgTest extends CatsEffectSuite {
  test("accepts valid config") {
    val file = mockRoot / ".scala-steward.conf"
    val content =
      """|updates.pin = [
         |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
         |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." }
         |]""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Success)
  }

  test("rejects config with a parsing failure") {
    val file = mockRoot / ".scala-steward.conf"
    val content =
      """|updates.pin  =? [
         |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
         |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." },
         |]""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Error)
  }

  test("rejects config with a decoding failure") {
    val file = mockRoot / ".scala-steward.conf"
    val content = """updatePullRequests = 123""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Error)
  }

  test("rejects non-existent config file") {
    val file = mockRoot / ".scala-steward.conf"
    val obtained = validateRepoConfigAlg.validateAndReport(file).runA(MockState.empty)
    assertIO(obtained, ExitCode.Error)
  }
}
