package org.scalasteward.core.repoconfig

import better.files.File
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.mock.MockContext.repoConfigAlg
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class RepoConfigAlgTest extends FunSuite with Matchers {
  test("config with all fields set") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File("/tmp/ws/fthomas/scala-steward/.scala-steward.conf")
    val content =
      """|updates.allowed = [ { groupId = "eu.timepit", artifactId = "refined", version = "0.8." } ]
         |updates.ignored = [ { groupId = "org.acme", version = "1.0" } ]
         |""".stripMargin
    val initialState = MockState.empty.add(configFile, content)
    val config = repoConfigAlg.getRepoConfig(repo).runA(initialState).unsafeRunSync()

    config shouldBe RepoConfig(
      updates = Some(
        UpdatesConfig(
          allowed = Some(List(UpdatePattern("eu.timepit", Some("refined"), Some("0.8.")))),
          ignored = Some(List(UpdatePattern("org.acme", None, Some("1.0"))))
        )
      )
    )
  }

  test("malformed config") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File("/tmp/ws/fthomas/scala-steward/.scala-steward.conf")
    val initialState = MockState.empty.add(configFile, """updates.ignored = [ "foo """)
    val (state, config) = repoConfigAlg.getRepoConfig(repo).run(initialState).unsafeRunSync()

    config shouldBe RepoConfig()
    state.logs.headOption.map { case (_, msg) => msg }.getOrElse("") should
      startWith("Failed to parse .scala-steward.conf")
  }
}
