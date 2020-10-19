package org.scalasteward.core.scalafmt

import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ScalafmtAlgTest extends AnyFunSuite with Matchers {
  test("getScalafmtVersion on unquoted version") {
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
    val (state, maybeUpdate) =
      scalafmtAlg.getScalafmtVersion(repo).run(initialState).unsafeRunSync()

    maybeUpdate shouldBe Some(Version("2.0.0-RC8"))
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

  test("getScalafmtVersion on quoted version") {
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
    val (_, maybeUpdate) = scalafmtAlg.getScalafmtVersion(repo).run(initialState).unsafeRunSync()
    maybeUpdate shouldBe Some(Version("2.0.0-RC8"))
  }

  test("editScalafmtConf") {
    // Tested in EditAlgTest
  }

  test("runScalafmt on explicit opt-out: Read config, but do not run scalafmt") {
    val repo = Repo("fthomas", "scala-steward")
    val rootDir = config.workspace.parent
    val repoDir = config.workspace / repo.owner / repo.repo
    val repoConf = repoDir / ".scala-steward.conf"
    val initialState = MockState.empty
      .add(repoConf, "scalafmt.runAfterUpgrading = false")
    val mainArtifactId = "scalafmt-core"

    val (state, _) = scalafmtAlg.runScalafmt(repo, mainArtifactId).run(initialState).unsafeRunSync()
    state shouldBe MockState.empty.copy(
      logs = state.logs, // do not care
      commands = Vector(
        List("read", s"$repoDir/.scala-steward.conf"),
        List("read", s"$rootDir/default.scala-steward.conf")
      ),
      files = Map(
        repoConf -> "scalafmt.runAfterUpgrading = false"
      )
    )
  }

  test("runScalafmt on explicit opt-in: Read config and run scalafmt") {
    val repo = Repo("fthomas", "scala-steward")
    val rootDir = config.workspace.parent
    val repoDir = config.workspace / repo.owner / repo.repo
    val repoConf = repoDir / ".scala-steward.conf"
    val initialState = MockState.empty
      .add(repoConf, "scalafmt.runAfterUpgrading = true")
    val mainArtifactId = "scalafmt-core"

    val (state, _) = scalafmtAlg.runScalafmt(repo, mainArtifactId).run(initialState).unsafeRunSync()
    state shouldBe MockState.empty.copy(
      logs = state.logs, // do not care
      commands = Vector(
        List("read", s"$repoDir/.scala-steward.conf"),
        List("read", s"$rootDir/default.scala-steward.conf"),
        List(
          "VAR1=val1",
          "VAR2=val2",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          s";scalafmtAll;scalafmtSbt"
        )
      ),
      files = Map(
        repoConf -> "scalafmt.runAfterUpgrading = true"
      )
    )
  }

  test("runScalafmt by default (implicitly enabled): Read config and run scalafmt") {
    val repo = Repo("fthomas", "scala-steward")
    val rootDir = config.workspace.parent
    val repoDir = config.workspace / repo.owner / repo.repo
    val initialState = MockState.empty
    val mainArtifactId = "scalafmt-core"

    val (state, _) = scalafmtAlg.runScalafmt(repo, mainArtifactId).run(initialState).unsafeRunSync()
    state shouldBe MockState.empty.copy(
      logs = state.logs, // do not care
      commands = Vector(
        List("read", s"$repoDir/.scala-steward.conf"),
        List("read", s"$rootDir/default.scala-steward.conf"),
        List(
          "VAR1=val1",
          "VAR2=val2",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          s";scalafmtAll;scalafmtSbt"
        )
      ),
      files = Map()
    )
  }

  test("runScalafmt: Do nothing when scalafmt-core is NOT updated") {
    val repo = Repo("fthomas", "scala-steward")
    val initialState = MockState.empty
    val mainArtifactId = "foo-bar"

    val (state, _) = scalafmtAlg.runScalafmt(repo, mainArtifactId).run(initialState).unsafeRunSync()
    state shouldBe MockState.empty
  }
}
