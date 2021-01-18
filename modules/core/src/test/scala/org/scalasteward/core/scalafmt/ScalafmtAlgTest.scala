package org.scalasteward.core.scalafmt

import munit.FunSuite
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockContext.context.scalafmtAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class ScalafmtAlgTest extends FunSuite {
  test("getScalafmtVersion on unquoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val initialState = MockState.empty.add(
      scalafmtConf,
      """maxColumn = 100
        |version=2.0.0-RC8
        |align.openParenCallSite = false
        |""".stripMargin
    )
    val (state, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).run(initialState).unsafeRunSync()
    val expectedState = MockState.empty.copy(
      commands = Vector(List("read", s"$repoDir/.scalafmt.conf")),
      files = Map(
        scalafmtConf ->
          """maxColumn = 100
            |version=2.0.0-RC8
            |align.openParenCallSite = false
            |""".stripMargin
      )
    )

    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
    assertEquals(state, expectedState)
  }

  test("getScalafmtVersion on quoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val initialState = MockState.empty.add(
      scalafmtConf,
      """maxColumn = 100
        |version="2.0.0-RC8"
        |align.openParenCallSite = false
        |""".stripMargin
    )
    val (_, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).run(initialState).unsafeRunSync()
    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
  }
}
