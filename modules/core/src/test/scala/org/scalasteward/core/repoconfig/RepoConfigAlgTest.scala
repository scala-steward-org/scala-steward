package org.scalasteward.core.repoconfig

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegInt
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{GroupId, Repo, SemVer, Update}
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.util.Nel
import scala.concurrent.duration._

class RepoConfigAlgTest extends FunSuite {
  test("default config is not empty") {
    val config = repoConfigAlg
      .readRepoConfig(Repo("repo-config-alg", "test-1"))
      .map(_.maybeRepoConfig)
      .map(repoConfigAlg.mergeWithGlobal)
      .runA(MockState.empty)
      .unsafeRunSync()

    assert(config =!= RepoConfig.empty)
  }

  test("config with all fields set") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = workspaceAlg.repoDir(repo).unsafeRunSync() / ".scala-steward.conf"
    val content =
      """|updates.allow  = [ { groupId = "eu.timepit" } ]
         |updates.pin  = [
         |                 { groupId = "eu.timepit", artifactId = "refined.1", version = "0.8." },
         |                 { groupId = "eu.timepit", artifactId = "refined.2", version = { prefix="0.8." } },
         |                 { groupId = "eu.timepit", artifactId = "refined.3", version = { suffix="jre" } },
         |                 { groupId = "eu.timepit", artifactId = "refined.4", version = { prefix="0.8.", suffix="jre" } }
         |               ]
         |updates.ignore = [ { groupId = "org.acme", version = "1.0" } ]
         |updates.allowPreReleases = [ { groupId = "eu.timepit" } ]
         |updates.limit = 4
         |updates.fileExtensions = [ ".txt" ]
         |pullRequests.frequency = "@weekly"
         |pullRequests.grouping = [
         |  { name = "patches", "title" = "Patch updates", "filter" = [{"version" = "patch"}] },
         |  { name = "minor_major", "title" = "Minor/major updates", "filter" = [{"version" = "minor"}, {"version" = "major"}] },
         |  { name = "typelevel", "title" = "Typelevel updates", "filter" = [{"group" = "org.typelevel"}, {"group" = "org.http4s"}] },
         |  { name = "my_libraries", "filter" = [{"artifact" = "my-library"}, {"artifact" = "my-other-library", "group" = "my-org"}] },
         |  { name = "all", "filter" = [{"group" = "*"}] }
         |]
         |dependencyOverrides = [
         |  { pullRequests.frequency = "@daily",   dependency = { groupId = "eu.timepit" } },
         |  { pullRequests.frequency = "@monthly", dependency = { groupId = "eu.timepit", artifactId = "refined.1" } },
         |  { pullRequests.frequency = "@weekly",  dependency = { groupId = "eu.timepit", artifactId = "refined.1", version = { prefix="1." } } },
         |]
         |commits.message = "Update ${artifactName} from ${currentVersion} to ${nextVersion}"
         |buildRoots = [ ".", "subfolder/subfolder" ]
         |assignees = [ "scala.steward" ]
         |reviewers = [ "scala.steward" ]
         |""".stripMargin
    val initialState = MockState.empty.addFiles(configFile -> content).unsafeRunSync()
    val obtained = repoConfigAlg
      .readRepoConfig(repo)
      .map(_.maybeRepoConfig.getOrElse(RepoConfig.empty))
      .runA(initialState)
      .unsafeRunSync()

    val expected = RepoConfig(
      pullRequests = PullRequestsConfig(
        frequency = Some(PullRequestFrequency.Timespan(7.days)),
        grouping = List(
          PullRequestGroup(
            name = "patches",
            title = "Patch updates".some,
            filter = NonEmptyList.of(
              PullRequestUpdateFilter(None, None, SemVer.Change.Patch.some)
                .getOrElse(fail("Should not be called"))
            )
          ),
          PullRequestGroup(
            name = "minor_major",
            title = "Minor/major updates".some,
            filter = NonEmptyList.of(
              PullRequestUpdateFilter(None, None, SemVer.Change.Minor.some)
                .getOrElse(fail("Should not be called")),
              PullRequestUpdateFilter(None, None, SemVer.Change.Major.some)
                .getOrElse(fail("Should not be called"))
            )
          ),
          PullRequestGroup(
            name = "typelevel",
            title = "Typelevel updates".some,
            filter = NonEmptyList.of(
              PullRequestUpdateFilter("org.typelevel".some).getOrElse(fail("Should not be called")),
              PullRequestUpdateFilter("org.http4s".some).getOrElse(fail("Should not be called"))
            )
          ),
          PullRequestGroup(
            name = "my_libraries",
            filter = NonEmptyList.of(
              PullRequestUpdateFilter(None, "my-library".some)
                .getOrElse(fail("Should not be called")),
              PullRequestUpdateFilter("my-org".some, "my-other-library".some)
                .getOrElse(fail("Should not be called"))
            )
          ),
          PullRequestGroup(
            name = "all",
            filter = NonEmptyList.of(
              PullRequestUpdateFilter("*".some).getOrElse(fail("Should not be called"))
            )
          )
        )
      ),
      updates = UpdatesConfig(
        allow = List(UpdatePattern("eu.timepit".g, None, None)),
        pin = List(
          UpdatePattern("eu.timepit".g, Some("refined.1"), Some(VersionPattern(Some("0.8.")))),
          UpdatePattern("eu.timepit".g, Some("refined.2"), Some(VersionPattern(Some("0.8.")))),
          UpdatePattern(
            "eu.timepit".g,
            Some("refined.3"),
            Some(VersionPattern(suffix = Some("jre")))
          ),
          UpdatePattern(
            "eu.timepit".g,
            Some("refined.4"),
            Some(VersionPattern(Some("0.8."), Some("jre")))
          )
        ),
        ignore = List(UpdatePattern("org.acme".g, None, Some(VersionPattern(Some("1.0"))))),
        allowPreReleases = List(UpdatePattern("eu.timepit".g, None, None)),
        limit = Some(NonNegInt.unsafeFrom(4)),
        fileExtensions = Some(List(".txt"))
      ),
      commits = CommitsConfig(
        message = Some("Update ${artifactName} from ${currentVersion} to ${nextVersion}")
      ),
      buildRoots = Some(List(BuildRootConfig.repoRoot, BuildRootConfig("subfolder/subfolder"))),
      dependencyOverrides = List(
        GroupRepoConfig(
          dependency = UpdatePattern(GroupId("eu.timepit"), None, None),
          pullRequests = PullRequestsConfig(
            frequency = Some(PullRequestFrequency.Timespan(1.day))
          )
        ),
        GroupRepoConfig(
          dependency = UpdatePattern(GroupId("eu.timepit"), Some("refined.1"), None),
          pullRequests = PullRequestsConfig(
            frequency = Some(PullRequestFrequency.Timespan(30.days))
          )
        ),
        GroupRepoConfig(
          dependency = UpdatePattern(
            GroupId("eu.timepit"),
            Some("refined.1"),
            Some(VersionPattern(prefix = Some("1.")))
          ),
          pullRequests = PullRequestsConfig(
            frequency = Some(PullRequestFrequency.Timespan(7.days))
          )
        )
      ),
      assignees = List("scala.steward"),
      reviewers = List("scala.steward")
    )
    assertEquals(obtained, expected)
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
    val repo = Repo("typelevel", "cats")
    val content = """buildRoots = [ "../../../etc" ]"""
    val config = RepoConfigAlg.parseRepoConfig(content).map(_.buildRootsOrDefault(repo))
    assertEquals(config, Right(Nil))
  }

  test("malformed config") {
    val repo = Repo("fthomas", "scala-steward")
    val configFile = workspaceAlg.repoDir(repo).unsafeRunSync() / ".scala-steward.conf"
    val initialState =
      MockState.empty.addFiles(configFile -> """updates.ignore = [ "foo """).unsafeRunSync()
    val (state, config) = repoConfigAlg.readRepoConfig(repo).runSA(initialState).unsafeRunSync()

    val startOfErrorMsg = "String: 1: List should have ]"
    val obtainedErrorMsg = config.maybeParsingError.map(_.getMessage.take(startOfErrorMsg.length))
    assertEquals(obtainedErrorMsg, Some(startOfErrorMsg))

    val log = state.trace.collectFirst { case Log((_, msg)) => msg }.getOrElse("")
    assert(clue(log).contains(startOfErrorMsg))
  }

  test("config file in .github/") {
    val repo = Repo("test", "dot-github-config")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val rootConfigFile = repoDir / ".scala-steward.conf"
    val dotGithubConfigFile = repoDir / ".github" / ".scala-steward.conf"
    val initialState = MockState.empty.addFiles(dotGithubConfigFile -> "").unsafeRunSync()
    val config = repoConfigAlg.readRepoConfig(repo).runA(initialState).unsafeRunSync()

    assert(!fileAlg.isRegularFile(rootConfigFile).unsafeRunSync())
    assert(fileAlg.isRegularFile(dotGithubConfigFile).unsafeRunSync())

    assert(config.maybeRepoConfig.isDefined)
  }

  test("config file in .config/") {
    val repo = Repo("test", "dot-config-config")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val rootConfigFile = repoDir / ".scala-steward.conf"
    val dotConfigConfigFile = repoDir / ".config" / ".scala-steward.conf"
    val initialState = MockState.empty.addFiles(dotConfigConfigFile -> "").unsafeRunSync()
    val config = repoConfigAlg.readRepoConfig(repo).runA(initialState).unsafeRunSync()

    assert(!fileAlg.isRegularFile(rootConfigFile).unsafeRunSync())
    assert(fileAlg.isRegularFile(dotConfigConfigFile).unsafeRunSync())

    assert(config.maybeRepoConfig.isDefined)
  }

  test("log warning on multiple config files") {
    val repo = Repo("test", "multiple-config")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val rootConfigFile = repoDir / ".scala-steward.conf"
    val dotConfigConfigFile = repoDir / ".config" / ".scala-steward.conf"
    val initialState =
      MockState.empty.addFiles(rootConfigFile -> "", dotConfigConfigFile -> "").unsafeRunSync()
    val (state, config) = repoConfigAlg.readRepoConfig(repo).runSA(initialState).unsafeRunSync()

    assert(fileAlg.isRegularFile(rootConfigFile).unsafeRunSync())
    assert(fileAlg.isRegularFile(dotConfigConfigFile).unsafeRunSync())

    assert(config.maybeRepoConfig.isDefined)

    val log = state.trace.collectFirst { case Log((_, msg)) => msg }.getOrElse("")
    assert(clue(log).contains("Ignored config file"))
  }

  test("configToIgnoreFurtherUpdates with single update") {
    val update = ("a".g % "b".a % "c" %> "d").single
    val config = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())
    val expected =
      RepoConfig(updates = UpdatesConfig(ignore = List(UpdatePattern("a".g, Some("b"), None))))
    assertEquals(config, expected)
  }

  test("configToIgnoreFurtherUpdates with group update") {
    val update = ("a".g % Nel.of("b".a, "e".a) % "c" %> "d").group
    val config = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())
    val expected =
      RepoConfig(updates = UpdatesConfig(ignore = List(UpdatePattern("a".g, None, None))))
    assertEquals(config, expected)
  }

  test("configToIgnoreFurtherUpdates with grouped update") {
    val update1 = ("a".g % "b".a % "1" %> "2").single
    val update2 = ("c".g % "d".a % "1" %> "2").single
    val update = Update.Grouped("my-group", None, List(update1, update2))
    val config = RepoConfigAlg
      .parseRepoConfig(RepoConfigAlg.configToIgnoreFurtherUpdates(update))
      .getOrElse(RepoConfig())
    val expected = RepoConfig(updates =
      UpdatesConfig(ignore =
        List(
          UpdatePattern(groupId = "a".g, artifactId = "b".some, None),
          UpdatePattern(groupId = "c".g, artifactId = "d".some, None)
        )
      )
    )
    assertEquals(config, expected)
  }

  test("config with postUpdateHook without group and artifact id") {
    val content =
      """|postUpdateHooks = [{
         |  command = ["sbt", "mySbtCommand"]
         |  commitMessage = "Updated with a hook!"
         |  }]
         |""".stripMargin
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(
      postUpdateHooks = List(
        PostUpdateHookConfig(
          groupId = None,
          artifactId = None,
          command = Nel.of("sbt", "mySbtCommand"),
          commitMessage = "Updated with a hook!"
        )
      ).some
    )

    assertEquals(config, Right(expected))
  }

  test("config with postUpdateHook with group and artifact id") {
    val content =
      """|postUpdateHooks = [{
         |  groupId = "eu.timepit"
         |  artifactId = "refined.1"
         |  command = ["sbt", "mySbtCommand"]
         |  commitMessage = "Updated with a hook!"
         |  }]
         |""".stripMargin
    val config = RepoConfigAlg.parseRepoConfig(content)
    val expected = RepoConfig(
      postUpdateHooks = List(
        PostUpdateHookConfig(
          groupId = Some("eu.timepit".g),
          artifactId = Some("refined.1"),
          command = Nel.of("sbt", "mySbtCommand"),
          commitMessage = "Updated with a hook!"
        )
      ).some
    )
    assertEquals(config, Right(expected))
  }
}
