package org.scalasteward.core.scalafmt

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.scalafmtAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class ScalafmtAlgTest extends FunSuite {
  test("getScalafmtVersion on unquoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / scalafmtConfName
    val initialState = MockState.empty
      .addFiles(scalafmtConf -> """maxColumn = 100
                                  |version=2.0.0-RC8
                                  |align.openParenCallSite = false
                                  |""".stripMargin)
      .unsafeRunSync()
    val (state, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).runSA(initialState).unsafeRunSync()
    val expectedState = initialState.copy(
      trace = Vector(Cmd("read", scalafmtConf.toString))
    )

    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
    assertEquals(state, expectedState)
  }

  test("getScalafmtVersion on quoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / scalafmtConfName
    val initialState = MockState.empty
      .addFiles(scalafmtConf -> """maxColumn = 100
                                  |version="2.0.0-RC8"
                                  |align.openParenCallSite = false
                                  |""".stripMargin)
      .unsafeRunSync()
    val (_, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).runSA(initialState).unsafeRunSync()
    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
  }
}
