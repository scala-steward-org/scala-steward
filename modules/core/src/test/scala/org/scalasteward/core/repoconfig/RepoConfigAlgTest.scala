package org.scalasteward.core.repoconfig

import better.files.File
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.mock.MockContext.repoConfigAlg
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class RepoConfigAlgTest extends FunSuite with Matchers {
  test("malformed config") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File("/tmp/ws/fthomas/scala-steward/.scala-steward.conf")
    val initialState = MockState.empty.add(configFile, """ignoreDependencies: ["foo """)
    val (state, config) = repoConfigAlg.getRepoConfig(repo).run(initialState).unsafeRunSync()

    config shouldBe RepoConfig()
    state.logs.headOption.map { case (_, msg) => msg }.getOrElse("") should
      startWith("Failed to parse .scala-steward.conf")
  }
}
