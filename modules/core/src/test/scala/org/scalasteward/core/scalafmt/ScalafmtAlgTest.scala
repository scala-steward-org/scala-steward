package org.scalasteward.core.scalafmt

import munit.FunSuite
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class ScalafmtAlgTest extends FunSuite {
  test("getScalafmtVersion on unquoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    (for {
      repoDir <- workspaceAlg.repoDir(repo).runA(MockState.empty)
      _ <- fileAlg.deleteForce(repoDir).runA(MockState.empty)
      scalafmtConf = repoDir / ".scalafmt.conf"
      scalafmtConfContent = """maxColumn = 100
                              |version=2.0.0-RC8
                              |align.openParenCallSite = false
                              |""".stripMargin
      initial <- MockState.empty.add(scalafmtConf, scalafmtConfContent).init
      (obtained, maybeVersion) <- scalafmtAlg.getScalafmtVersion(buildRoot).run(initial)
      expected = MockState.empty.copy(
        trace = Vector(Cmd("read", scalafmtConf.pathAsString)),
        files = Map(
          scalafmtConf -> """maxColumn = 100
                            |version=2.0.0-RC8
                            |align.openParenCallSite = false
                            |""".stripMargin
        )
      )
    } yield {
      assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
      assertEquals(obtained, expected)
    }).unsafeRunSync()
  }

  test("getScalafmtVersion on quoted version") {
    val repo = Repo("scalafmt-alg", "test2")
    val buildRoot = BuildRoot(repo, ".")
    (for {
      repoDir <- workspaceAlg.repoDir(repo).runA(MockState.empty)
      _ <- fileAlg.deleteForce(repoDir).runA(MockState.empty)
      scalafmtConf = repoDir / ".scalafmt.conf"
      scalafmtConfContent = """maxColumn = 100
                              |version="2.0.0-RC8"
                              |align.openParenCallSite = false
                              |""".stripMargin
      initial <- MockState.empty.add(scalafmtConf, scalafmtConfContent).init
      maybeVersion <- scalafmtAlg.getScalafmtVersion(buildRoot).runA(initial)
    } yield assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))).unsafeRunSync()
  }
}
