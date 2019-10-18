package org.scalasteward.core.repoconfig

import better.files.File
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.mock.MockContext.repoConfigAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RepoConfigAlgTest extends AnyFunSuite with Matchers {
  test("config with all fields set") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val content =
      """|updates.allow  = [ { groupId = "eu.timepit", artifactId = "refined", version = "0.8." } ]
         |updates.ignore = [ { groupId = "org.acme", version = "1.0" } ]
         |""".stripMargin
    val initialState = MockState.empty.add(configFile, content)
    val config = repoConfigAlg.readRepoConfigOrDefault(repo).runA(initialState).unsafeRunSync()

    config shouldBe RepoConfig(
      updates = UpdatesConfig(
        allow = List(UpdatePattern(GroupId("eu.timepit"), Some("refined"), Some("0.8."))),
        ignore = List(UpdatePattern(GroupId("org.acme"), None, Some("1.0")))
      )
    )
  }

  test("config with 'updateBranch disabled") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val content = "updatePullRequests = false"
    val initialState = MockState.empty.add(configFile, content)
    val config = repoConfigAlg.readRepoConfigOrDefault(repo).runA(initialState).unsafeRunSync()

    config shouldBe RepoConfig(updatePullRequests = false)
  }

  test("malformed config") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val initialState = MockState.empty.add(configFile, """updates.ignore = [ "foo """)
    val (state, config) =
      repoConfigAlg.readRepoConfigOrDefault(repo).run(initialState).unsafeRunSync()

    config shouldBe RepoConfig()
    state.logs.headOption.map { case (_, msg) => msg }.getOrElse("") should
      startWith("Failed to parse .scala-steward.conf")
  }

  test("configToIgnoreFurtherUpdates with single update") {
    val update = Update.Single(GroupId("a"), "b", "c", Nel.of("d"))
    val repoConfig = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())

    repoConfig shouldBe RepoConfig(
      updates = UpdatesConfig(
        ignore = List(UpdatePattern(GroupId("a"), Some("b"), None))
      )
    )
  }

  test("configToIgnoreFurtherUpdates with group update") {
    val update = Update.Group(GroupId("a"), Nel.of("b", "e"), "c", Nel.of("d"))
    val repoConfig = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())

    repoConfig shouldBe RepoConfig(
      updates = UpdatesConfig(ignore = List(UpdatePattern(GroupId("a"), None, None)))
    )
  }
}
