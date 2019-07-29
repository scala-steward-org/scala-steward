package org.scalasteward.core.scalafmt

import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class ScalafmtAlgTest extends FunSuite with Matchers {

  test("getScalafmtUpdate on unquoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val initialState = MockState.empty.add(
      scalafmtConf,
      """maxColumn = 100
        |version=2.0.0-RC8
        |align.openParenCallSite = false
        |""".stripMargin
    )
    val (state, maybeUpdate) = scalafmtAlg.getScalafmtUpdate(repo).run(initialState).unsafeRunSync()

    maybeUpdate shouldBe Some(
      Update.Single("org.scalameta", "scalafmt", "2.0.0-RC8", Nel.of(latestScalafmtVersion.value))
    )
    state shouldBe MockState.empty.copy(
      commands = Vector(List("read", s"$repoDir/.scalafmt.conf")),
      files = Map(
        scalafmtConf ->
          """maxColumn = 100
            |version=2.0.0-RC8
            |align.openParenCallSite = false
            |""".stripMargin
      )
    )
  }

  test("getScalafmtUpdate on quoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val initialState = MockState.empty.add(
      scalafmtConf,
      """maxColumn = 100
        |version="2.0.0-RC8"
        |align.openParenCallSite = false
        |""".stripMargin
    )
    val (_, maybeUpdate) = scalafmtAlg.getScalafmtUpdate(repo).run(initialState).unsafeRunSync()
    maybeUpdate shouldBe Some(
      Update.Single("org.scalameta", "scalafmt", "2.0.0-RC8", Nel.of(latestScalafmtVersion.value))
    )
  }

  test("editScalafmtConf") {
    // Tested in EditAlgTest
  }

}
