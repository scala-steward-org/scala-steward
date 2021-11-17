package org.scalasteward.core.repoconfig

import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegInt
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.mock.MockContext.context.repoConfigAlg
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.mock.{MockConfig, MockState}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import scala.concurrent.duration._

class RepoConfigAlgTest extends FunSuite {
  test("default config is not empty") {
    val config = repoConfigAlg
      .readRepoConfig(Repo("repo-config-alg", "test-1"))
      .map(repoConfigAlg.mergeWithGlobal)
      .runA(MockState.empty)
      .unsafeRunSync()

    assert(config =!= RepoConfig.empty)
  }

  test("config with all fields set") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = MockConfig.config.workspace / "fthomas/scala-steward/.scala-steward.conf"
    val content =
      """|updates.allow  = [ { groupId = "eu.timepit"} ]
         |updates.pin  = [
         |                 { groupId = "eu.timepit", artifactId = "refined.1", version = "0.8." },
         |                 { groupId = "eu.timepit", artifactId = "refined.2", version = { prefix="0.8." } },
         |                 { groupId = "eu.timepit", artifactId = "refined.3", version = { suffix="jre" } },
         |                 { groupId = "eu.timepit", artifactId = "refined.4", version = { prefix="0.8.", suffix="jre" } }
         |               ]
         |updates.ignore = [ { groupId = "org.acme", version = "1.0" } ]
         |updates.limit = 4
         |updates.fileExtensions = [ ".txt" ]
         |pullRequests.frequency = "@weekly"
         |commits.message = "Update ${artifactName} from ${currentVersion} to ${nextVersion}"
         |buildRoots = [ ".", "subfolder/subfolder" ]
         |""".stripMargin
    val initialState = MockState.empty.addFiles(configFile -> content).unsafeRunSync()
    val config = repoConfigAlg
      .readRepoConfig(repo)
      .map(_.getOrElse(RepoConfig.empty))
      .runA(initialState)
      .unsafeRunSync()

    val expected = RepoConfig(
      pullRequests = PullRequestsConfig(frequency = Some(PullRequestFrequency.Timespan(7.days))),
      updates = UpdatesConfig(
        allow = List(UpdatePattern(GroupId("eu.timepit"), None, None)),
        pin = List(
          UpdatePattern(
            GroupId("eu.timepit"),
            Some("refined.1"),
            Some(VersionPattern(Some("0.8.")))
          ),
          UpdatePattern(
            GroupId("eu.timepit"),
            Some("refined.2"),
            Some(VersionPattern(Some("0.8.")))
          ),
          UpdatePattern(
            GroupId("eu.timepit"),
            Some("refined.3"),
            Some(VersionPattern(suffix = Some("jre")))
          ),
          UpdatePattern(
            GroupId("eu.timepit"),
            Some("refined.4"),
            Some(VersionPattern(Some("0.8."), Some("jre")))
          )
        ),
        ignore = List(
          UpdatePattern(GroupId("org.acme"), None, Some(VersionPattern(Some("1.0"))))
        ),
        limit = Some(NonNegInt.unsafeFrom(4)),
        fileExtensions = Some(List(".txt"))
      ),
      commits = CommitsConfig(
        message = Some("Update ${artifactName} from ${currentVersion} to ${nextVersion}")
      ),
      buildRoots = Some(List(BuildRootConfig.repoRoot, BuildRootConfig("subfolder/subfolder")))
    )
    assertEquals(config, expected)
  }

  test("config with 'updatePullRequests = false'") {
    val content = "updatePullRequests = false"
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(updatePullRequests = Some(PullRequestUpdateStrategy.Never))
    assertEquals(config, Right(expected))
  }

  test("config with 'updatePullRequests = true'") {
    val content = "updatePullRequests = true"
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(updatePullRequests = Some(PullRequestUpdateStrategy.OnConflicts))
    assertEquals(config, Right(expected))
  }

  test("config with 'updatePullRequests = always'") {
    val content = """updatePullRequests = "always" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(updatePullRequests = Some(PullRequestUpdateStrategy.Always))
    assertEquals(config, Right(expected))
  }

  test("config with 'updatePullRequests = on-conflicts'") {
    val content = """updatePullRequests = "on-conflicts" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(updatePullRequests = Some(PullRequestUpdateStrategy.OnConflicts))
    assertEquals(config, Right(expected))
  }

  test("config with 'updatePullRequests = never'") {
    val content = """updatePullRequests = "never" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(updatePullRequests = Some(PullRequestUpdateStrategy.Never))
    assertEquals(config, Right(expected))
  }

  test("config with 'updatePullRequests = foo'") {
    val content = """updatePullRequests = foo """
    val obtained = RepoConfigAlg.parseRepoConfig(content).map(_.updatePullRequestsOrDefault)
    val expected = PullRequestUpdateStrategy.default
    assertEquals(obtained, Right(expected))
  }

  test("config with 'pullRequests.frequency = @asap'") {
    val content = """pullRequests.frequency = "@asap" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected =
      RepoConfig(pullRequests = PullRequestsConfig(frequency = Some(PullRequestFrequency.Asap)))
    assertEquals(config, Right(expected))
  }

  test("config with 'pullRequests.frequency = @daily'") {
    val content = """pullRequests.frequency = "@daily" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(pullRequests =
      PullRequestsConfig(frequency = Some(PullRequestFrequency.Timespan(1.day)))
    )
    assertEquals(config, Right(expected))
  }

  test("config with 'pullRequests.frequency = @monthly'") {
    val content = """pullRequests.frequency = "@monthly" """
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(pullRequests =
      PullRequestsConfig(frequency = Some(PullRequestFrequency.Timespan(30.days)))
    )
    assertEquals(config, Right(expected))
  }

  test("config with 'scalafmt.runAfterUpgrading = true'") {
    val content = "scalafmt.runAfterUpgrading = true"
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(scalafmt = ScalafmtConfig(runAfterUpgrading = Some(true)))
    assertEquals(config, Right(expected))
  }

  test("build root with '..'") {
    val content = """buildRoots = [ "../../../etc" ]"""
    val config = RepoConfigAlg.parseRepoConfig(content).map(_.buildRootsOrDefault)
    assertEquals(config, Right(Nil))
  }

  test("malformed config") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = MockConfig.config.workspace / "fthomas/scala-steward/.scala-steward.conf"
    val initialState =
      MockState.empty.addFiles(configFile -> """updates.ignore = [ "foo """).unsafeRunSync()
    val (state, config) = repoConfigAlg.readRepoConfig(repo).runSA(initialState).unsafeRunSync()

    assertEquals(config, None)
    val log = state.trace.collectFirst { case Log((_, msg)) => msg }.getOrElse("")
    assert(clue(log).startsWith("Failed to parse .scala-steward.conf"))
  }

  test("configToIgnoreFurtherUpdates with single update") {
    val update = Update.Single("a" % "b" % "c", Nel.of("d"))
    val config = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())
    val expected = RepoConfig(updates =
      UpdatesConfig(ignore = List(UpdatePattern(GroupId("a"), Some("b"), None)))
    )
    assertEquals(config, expected)
  }

  test("configToIgnoreFurtherUpdates with group update") {
    val update = Update.Group("a" % Nel.of("b", "e") % "c", Nel.of("d"))
    val config = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())
    val expected =
      RepoConfig(updates = UpdatesConfig(ignore = List(UpdatePattern(GroupId("a"), None, None))))
    assertEquals(config, expected)
  }
}
