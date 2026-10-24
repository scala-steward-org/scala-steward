/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.nurture

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.literals.*
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{
  Dependency,
  DependencyInfo,
  NoLongerNeeded,
  Repo,
  RepoData,
  Update,
  UpdateData
}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.edit.EditAttempt.UpdateEdit
import org.scalasteward.core.forge.data.{
  NewPullRequestData,
  PullRequestNumber,
  PullRequestState,
  RepoOut,
  UserOut
}
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeRepoAlg}
import org.scalasteward.core.git.{branchFor, Branch, Commit, GitAlg, Sha1}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.MockForgeApiAlg.{MockForgeState, RepoState}
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{
  GitHubAuth,
  MockConfig,
  MockEff,
  MockEffOps,
  MockForgeApiAlg,
  MockState
}
import org.scalasteward.core.repoconfig.{PullRequestsConfig, RepoConfig}
import org.scalasteward.core.util.{Nel, UrlChecker}
import org.typelevel.log4cats.Logger

class NurtureAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  test("preparePullRequest") {
    val nurtureAlg = context.nurtureAlg
    val repo = Repo("scala-steward-org", "scala-steward")
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0"
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(dependency, Nil)).withMavenCentral)
    )
    val repoData = RepoData(
      repo,
      repoCache,
      RepoConfig(assignees = List("foo").some, reviewers = List("bar").some)
    )
    val fork = Repo("scala-steward", "scala-steward")
    val update = (dependency %> "3.4.0").single
    val baseBranch = Branch("main")
    val updateBranch = Branch("update/cats-effect-3.4.0")
    val updateData = UpdateData(repoData, fork, update, baseBranch, dummySha1, updateBranch)
    val edits = List(UpdateEdit(update, Commit(dummySha1)))
    val state = MockState.empty.copy(clientResponses = GitHubAuth.api(List.empty) <+> HttpApp {
      case HEAD -> Root / "typelevel" / "cats-effect"                                 => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "releases" / "tag" / "v3.4.0" => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "compare" / "v3.3.0...v3.4.0" => Ok()
      case _                                                                          => NotFound()
    })
    val obtained = nurtureAlg.preparePullRequest(updateData, edits).runA(state)
    val expected = NewPullRequestData(
      title = "Update cats-effect to 3.4.0",
      body =
        raw"""## About this PR
             |📦 Updates [org.typelevel:cats-effect](https://github.com/typelevel/cats-effect) from `3.3.0` to `3.4.0`
             |
             |📜 [GitHub Release Notes](https://github.com/typelevel/cats-effect/releases/tag/v3.4.0) - [Version Diff](https://github.com/typelevel/cats-effect/compare/v3.3.0...v3.4.0)
             |
             |## Usage
             |✅ **Please merge!**
             |
             |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
             |
             |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
             |
             |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
             |
             |_Have a fantastic day writing Scala!_
             |
             |<details>
             |<summary>⚙ Adjust future updates</summary>
             |
             |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
             |```
             |updates.ignore = [ { groupId = "org.typelevel", artifactId = "cats-effect" } ]
             |```
             |Or, add this to slow down future updates of this dependency:
             |```
             |dependencyOverrides = [{
             |  pullRequests = { frequency = "30 days" },
             |  dependency = { groupId = "org.typelevel", artifactId = "cats-effect" }
             |}]
             |```
             |</details>
             |
             |<sup>
             |labels: library-update, early-semver-minor, semver-spec-minor, version-scheme:early-semver, commit-count:1
             |</sup>
             |
             |<!-- scala-steward = {
             |  "Update" : {
             |    "ForArtifactId" : {
             |      "crossDependency" : [
             |        {
             |          "groupId" : "org.typelevel",
             |          "artifactId" : {
             |            "name" : "cats-effect",
             |            "maybeCrossName" : "cats-effect_2.13"
             |          },
             |          "version" : "3.3.0",
             |          "sbtVersion" : null,
             |          "scalaVersion" : null,
             |          "configurations" : null
             |        }
             |      ],
             |      "newerVersions" : [
             |        "3.4.0"
             |      ],
             |      "newerGroupId" : null,
             |      "newerArtifactId" : null
             |    }
             |  },
             |  "Labels" : [
             |    "library-update",
             |    "early-semver-minor",
             |    "semver-spec-minor",
             |    "version-scheme:early-semver",
             |    "commit-count:1"
             |  ]
             |} -->""".stripMargin.trim,
      head = "scala-steward:update/cats-effect-3.4.0",
      base = baseBranch,
      labels = List(
        "library-update",
        "early-semver-minor",
        "semver-spec-minor",
        "version-scheme:early-semver",
        "commit-count:1"
      ),
      assignees = List("foo"),
      reviewers = List("bar")
    )
    assertIO(obtained, expected)
  }

  test("preparePullRequest should not set labels if ForgeConfig.addLabels = false") {
    def findLabelLine(L: Array[String]) =
      L.zipWithIndex.find(_._1.contains("labels: ")).map(x => L(x._2)).getOrElse("")

    val config =
      MockConfig.config.copy(forgeCfg = MockConfig.config.forgeCfg.copy(addLabels = false))
    val nurtureAlg = context(config).nurtureAlg
    val repo = Repo("scala-steward-org", "scala-steward")
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0"
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(dependency, Nil)).withMavenCentral)
    )
    val repoData = RepoData(
      repo,
      repoCache,
      RepoConfig(assignees = List("foo").some, reviewers = List("bar").some)
    )
    val fork = Repo("scala-steward", "scala-steward")
    val update = (dependency %> "3.4.0").single
    val baseBranch = Branch("main")
    val updateBranch = Branch("update/cats-effect-3.4.0")
    val updateData = UpdateData(repoData, fork, update, baseBranch, dummySha1, updateBranch)
    val edits = List(UpdateEdit(update, Commit(dummySha1)))
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case HEAD -> Root / "typelevel" / "cats-effect"                                 => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "releases" / "tag" / "v3.4.0" => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "compare" / "v3.3.0...v3.4.0" => Ok()
      case _                                                                          => NotFound()
    })
    nurtureAlg.preparePullRequest(updateData, edits).runA(state).map { obtained =>
      assert(obtained.labels.isEmpty)
      val labelLine = findLabelLine(obtained.body.split("\n"))
      assertEquals(
        labelLine,
        "labels: library-update, early-semver-minor, semver-spec-minor, version-scheme:early-semver, commit-count:1"
      )
    }
  }

  test(
    "preparePullRequest should set custom labels if PullRequestsConfig.customLabels is provided"
  ) {
    def findLabelLine(L: Array[String]) =
      L.zipWithIndex.find(_._1.contains("labels: ")).map(x => L(x._2)).getOrElse("")

    val nurtureAlg = context.nurtureAlg
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0"
    val customLabels = List("custom-label-1", "custom-label-2")
    val repoData =
      RepoData(
        repo = Repo("scala-steward-org", "scala-steward"),
        cache = dummyRepoCache,
        config =
          RepoConfig(pullRequests = PullRequestsConfig(customLabels = customLabels.some).some)
      )
    val update = (dependency %> "3.4.0").single
    val baseBranch = Branch("main")
    val updateBranch = Branch("update/cats-effect-3.4.0")
    val updateData =
      UpdateData(repoData, repoData.repo, update, baseBranch, dummySha1, updateBranch)
    val edits = List(UpdateEdit(update, Commit(dummySha1)))
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case HEAD -> Root / "typelevel" / "cats-effect"                                 => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "releases" / "tag" / "v3.4.0" => Ok()
      case HEAD -> Root / "typelevel" / "cats-effect" / "compare" / "v3.3.0...v3.4.0" => Ok()
      case _                                                                          => NotFound()
    })
    nurtureAlg.preparePullRequest(updateData, edits).runA(state).map { obtained =>
      assertEquals(
        obtained.labels,
        customLabels ++ List(
          "library-update",
          "early-semver-minor",
          "semver-spec-minor",
          "commit-count:1"
        )
      )

      val labelLine = findLabelLine(obtained.body.split("\n"))

      assertEquals(
        labelLine,
        "labels: custom-label-1, custom-label-2, library-update, early-semver-minor, semver-spec-minor, commit-count:1"
      )
    }
  }

  /** A `NurtureAlg` whose forge is the in-memory `MockForgeApiAlg`, so that PR comments and state
    * changes can be inspected.
    */
  private def nurtureAlgWithMockForge: (NurtureAlg[MockEff], PullRequestService[MockEff]) = {
    implicit val forgeApiAlg: ForgeApiAlg[MockEff] = MockForgeApiAlg.mockApiAlg
    implicit val pullRequestService: PullRequestService[MockEff] =
      new PullRequestService[MockEff](context.pullRequestRepository, forgeApiAlg)
    implicit val coursierAlg: CoursierAlg[MockEff] = context.coursierAlg
    implicit val editAlg: EditAlg[MockEff] = context.editAlg
    implicit val forgeRepoAlg: ForgeRepoAlg[MockEff] = context.forgeRepoAlg
    implicit val gitAlg: GitAlg[MockEff] = context.gitAlg
    implicit val logger: Logger[MockEff] = context.logger
    implicit val updateInfoUrlFinder: UpdateInfoUrlFinder[MockEff] = context.updateInfoUrlFinder
    implicit val urlChecker: UrlChecker[MockEff] = context.urlChecker
    (new NurtureAlg[MockEff](MockConfig.config.forgeCfg), pullRequestService)
  }

  /** Everything needed to run `closeNoLongerNeededPullRequests` against a repo that has an open
    * Steward PR for `update` while its base branch has the same artifact at `baseVersions` (none
    * means the dependency was removed).
    */
  private class NoLongerNeededFixture(
      repoName: String,
      update: Update.ForArtifactId,
      baseVersions: List[String],
      branchAuthors: List[String],
      repoConfig: RepoConfig = RepoConfig(),
      emptyScan: Boolean = false,
      pendingUpdates: List[Update.ForArtifactId] = Nil,
      moreDependencies: List[Dependency] = Nil
  ) {
    val baseBranch: Branch = Branch("main")
    val repo: Repo = Repo("no-longer-needed", repoName)
    // every real build has some other dependency, unless the scan is broken
    val otherDependency: Dependency = "org.scala-lang".g % "scala-library".a % "2.13.16"
    val repoOut: RepoOut = RepoOut(
      repo.repo,
      UserOut(repo.owner),
      parent = None,
      clone_url = uri"https://github.com/no-longer-needed/repo.git",
      default_branch = baseBranch
    )
    val fork: RepoOut =
      repoOut.copy(owner = UserOut("bot-doe"), parent = Some(repoOut))
    val updateBranch: Branch = branchFor(update, None)
    val remoteUpdateBranch: Branch = updateBranch.withPrefix("origin/")
    val baseDependencies: List[Dependency] =
      if (emptyScan) List.empty
      else
        otherDependency :: moreDependencies :::
          baseVersions.map(v => update.crossDependency.head.copy(version = v.v))
    val otherSha1: Sha1 = Sha1.unsafeFrom("b2ced5793c2832ada8c14ba5c77e51c4bc9656a9")

    /** The repo as seen in a cache refresh at `sha1`. */
    def repoDataAt(sha1: Sha1 = dummySha1, configParsingError: Option[String] = None): RepoData =
      RepoData(
        repo,
        dummyRepoCache.copy(
          sha1 = sha1,
          dependencyInfos = List(baseDependencies.map(DependencyInfo(_, Nil)).withMavenCentral),
          maybeRepoConfigParsingError = configParsingError
        ),
        repoConfig
      )
    val repoData: RepoData = repoDataAt()
    val updateData: UpdateData =
      UpdateData(repoData, repo, update, baseBranch, dummySha1, updateBranch)
    val repoDir = MockConfig.config.workspace / repo.owner / repo.repo

    val initialState: MockState =
      MockState.empty.copy(
        forgeState = MockForgeState(Map(repo -> RepoState())),
        commandOutputs = Map(
          Cmd.git(repoDir, "branch", "--list", "--no-color", "--all", remoteUpdateBranch.name) ->
            Right(List(s"  remotes/${remoteUpdateBranch.name}")),
          Cmd.git(repoDir, "log", "--pretty=format:'%an'", s"main..${remoteUpdateBranch.name}") ->
            Right(branchAuthors)
        )
      )

    /** `cloneExists` looks at the real file system, so the clone is made to exist for the duration
      * of `program` and removed afterwards.
      */
    def withClone[A](program: MockEff[A], state: MockState = initialState): (MockState, A) = {
      (repoDir / ".git").createDirectories()
      try program.runSA(state).unsafeRunSync()(using cats.effect.unsafe.implicits.global)
      finally repoDir.delete(swallowIOExceptions = true)
    }

    /** Creates the PR and then runs `closeNoLongerNeededPullRequests` once per element of
      * `refreshes`, each standing for the repo cache of one run.
      */
    def run(refreshes: List[RepoData]): (MockState, (List[String], PullRequestState, Boolean)) = {
      val (nurtureAlg, pullRequestService) = nurtureAlgWithMockForge
      val program = for {
        pr <- pullRequestService.createPullRequest(
          updateData,
          NewPullRequestData.from(updateData, branchName = updateBranch.name)
        )
        _ <- refreshes.traverse_(
          nurtureAlg.closeNoLongerNeededPullRequests(_, fork, pendingUpdates)
        )
        closedAsNoLongerNeeded <- pullRequestService.wasClosedAsNoLongerNeeded(repo, pr.html_url)
      } yield (pr.number, closedAsNoLongerNeeded)
      val (state, (number, closedAsNoLongerNeeded)) = withClone(program)
      val prState = state.forgeState.repos(repo).prs(number)
      (state, (prState.comments, prState.current.state, closedAsNoLongerNeeded))
    }

    def run(runs: Int): (MockState, (List[String], PullRequestState, Boolean)) =
      run(List.fill(runs)(repoData))

    def deletedRemoteBranch(state: MockState): Boolean =
      state.trace.exists {
        case Cmd(cmd) => cmd.contains("push") && cmd.contains(updateBranch.name)
        case _        => false
      }
  }

  private val thingA = "com.example".g % "thing-A".a % "1.0.0"
  private val closing = "so this PR is no longer needed and is closed."

  test("closeNoLongerNeededPullRequests closes a PR whose version is already in the base branch") {
    val fixture =
      new NoLongerNeededFixture(
        "closes",
        (thingA %> "2.0.0").single,
        List("2.0.0"),
        List("Bot Doe")
      )
    val (state, (comments, prState, closedAsNoLongerNeeded)) = fixture.run(runs = 1)

    assertEquals(prState, PullRequestState.Closed)
    assertEquals(
      comments,
      List(
        "The base branch `main` already contains com.example:thing-A in version 2.0.0, " +
          s"which is the same as or newer than the 2.0.0 this PR proposes, $closing"
      )
    )
    assert(closedAsNoLongerNeeded)
    assert(fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests closes a PR whose dependency stayed removed") {
    val fixture =
      new NoLongerNeededFixture("removed", (thingA %> "2.0.0").single, List.empty, List("Bot Doe"))
    // one scan without the dependency is not enough, it could be a bad scan
    val (_, (commentsAfterOne, prStateAfterOne, _)) = fixture.run(runs = 1)
    assertEquals(prStateAfterOne, PullRequestState.Open)
    assertEquals(commentsAfterOne, List.empty)

    val (state, (comments, prState, closedAsNoLongerNeeded)) =
      fixture.run(List(fixture.repoData, fixture.repoDataAt(fixture.otherSha1)))
    assertEquals(prState, PullRequestState.Closed)
    assertEquals(
      comments,
      List(s"Scala Steward no longer finds com.example:thing-A in the build of `main`, $closing")
    )
    assert(closedAsNoLongerNeeded)
    assert(fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests keeps a PR with commits by others open and silent") {
    val fixture = new NoLongerNeededFixture(
      "others",
      (thingA %> "2.0.0").single,
      List("2.0.0"),
      List("Bot Doe", "Jane")
    )
    // several runs must not pile up comments on the PR that stays open
    val (state, (comments, prState, closedAsNoLongerNeeded)) = fixture.run(runs = 3)

    assertEquals(prState, PullRequestState.Open)
    assertEquals(comments, List.empty)
    assert(!closedAsNoLongerNeeded)
    assert(!fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests keeps a PR that an older cross-built module still shares") {
    // foo_2.13 was bumped by hand to 1.11, foo_3 at 1.2 still waits for the same PR
    val foo213 = "com.example".g % ("foo", "foo_2.13").a % "1.10"
    val foo3 = "com.example".g % ("foo", "foo_3").a % "1.2"
    val fixture = new NoLongerNeededFixture(
      "shared",
      (foo213 %> "1.11").single,
      List("1.11"),
      List("Bot Doe"),
      pendingUpdates = List((foo3 %> "1.11").single),
      moreDependencies = List(foo3)
    )
    val (state, (comments, prState, _)) = fixture.run(runs = 1)

    assertEquals(prState, PullRequestState.Open)
    assertEquals(comments, List.empty)
    assert(!fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests does nothing if the repo config could not be parsed") {
    val fixture =
      new NoLongerNeededFixture(
        "bad-config",
        (thingA %> "2.0.0").single,
        List.empty,
        List("Bot Doe")
      )
    val badConfig = fixture.repoDataAt(configParsingError = Some("boom"))
    val (state, (comments, prState, _)) =
      fixture.run(
        List(badConfig, badConfig.copy(cache = badConfig.cache.copy(sha1 = fixture.otherSha1)))
      )

    assertEquals(prState, PullRequestState.Open)
    assertEquals(comments, List.empty)
    assert(!fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests does nothing if the scan found no dependencies at all") {
    val fixture = new NoLongerNeededFixture(
      "empty-scan",
      (thingA %> "2.0.0").single,
      List.empty,
      List("Bot Doe"),
      emptyScan = true
    )
    val (state, (comments, prState, _)) =
      fixture.run(List(fixture.repoData, fixture.repoDataAt(fixture.otherSha1)))

    assertEquals(prState, PullRequestState.Open)
    assertEquals(comments, List.empty)
    assert(!fixture.deletedRemoteBranch(state))
  }

  test("closeNoLongerNeededPullRequests can be disabled with pullRequests.closeNoLongerNeeded") {
    val fixture = new NoLongerNeededFixture(
      "disabled",
      (thingA %> "2.0.0").single,
      List("2.0.0"),
      List("Bot Doe"),
      RepoConfig(pullRequests = Some(PullRequestsConfig(closeNoLongerNeeded = Some(false))))
    )
    val (_, (comments, prState, _)) = fixture.run(runs = 1)

    assertEquals(prState, PullRequestState.Open)
    assertEquals(comments, List.empty)
  }

  test("commentForBeingNoLongerNeeded lists the versions found, sorted") {
    val (nurtureAlg, _) = nurtureAlgWithMockForge
    val versions = List("1.20", "1.11", "1.12").map(v => thingA.copy(version = v.v))
    assertEquals(
      nurtureAlg.commentForBeingNoLongerNeeded(
        Branch("main"),
        (thingA.copy(version = "1.10".v) %> "1.11").single,
        NoLongerNeeded.AlreadyApplied(Nel.fromListUnsafe(versions))
      ),
      "The base branch `main` already contains com.example:thing-A in versions 1.11, 1.12 and " +
        s"1.20, all of which are the same as or newer than the 1.11 this PR proposes, $closing"
    )
  }

  test("commentForBeingNoLongerNeeded names every artifact of a group update") {
    val (nurtureAlg, _) = nurtureAlgWithMockForge
    val update = ("org.specs2".g % Nel.of("specs2-core".a, "specs2-scalacheck".a) %
      "3.9.4" %> "3.9.5").group
    val core = "org.specs2".g % "specs2-core".a % "3.9.5"
    val scalacheck = "org.specs2".g % "specs2-scalacheck".a % "3.9.5"
    val scalacheckNewer = "org.specs2".g % "specs2-scalacheck".a % "3.9.6"

    assertEquals(
      nurtureAlg.commentForBeingNoLongerNeeded(
        Branch("main"),
        update,
        NoLongerNeeded.AlreadyApplied(Nel.of(scalacheckNewer, core, scalacheck))
      ),
      "The base branch `main` already contains org.specs2:specs2-core in version 3.9.5 and " +
        "org.specs2:specs2-scalacheck in versions 3.9.5 and 3.9.6, all of which are the same as " +
        s"or newer than the 3.9.5 this PR proposes, $closing"
    )
    // several artifacts at one version are still "all of which are"
    assertEquals(
      nurtureAlg.commentForBeingNoLongerNeeded(
        Branch("main"),
        update,
        NoLongerNeeded.AlreadyApplied(Nel.of(core, scalacheck))
      ),
      "The base branch `main` already contains org.specs2:specs2-core in version 3.9.5 and " +
        "org.specs2:specs2-scalacheck in version 3.9.5, all of which are the same as " +
        s"or newer than the 3.9.5 this PR proposes, $closing"
    )
    assertEquals(
      nurtureAlg.commentForBeingNoLongerNeeded(
        Branch("main"),
        update,
        NoLongerNeeded.DependencyRemoved
      ),
      "Scala Steward no longer finds org.specs2:specs2-core and org.specs2:specs2-scalacheck " +
        s"in the build of `main`, $closing"
    )
  }

  /** Runs `nurture` for an update whose only PR is closed, either by somebody or by Scala Steward
    * because the base branch did not need it, and returns the log messages and the PRs of the repo
    * afterwards.
    */
  private def nurtureWithClosedPr(
      repoName: String,
      closedReason: Option[ClosedReason]
  ): (List[String], List[(PullRequestNumber, PullRequestState)]) = {
    val update = (thingA %> "2.0.0").single
    val fixture = new NoLongerNeededFixture(repoName, update, List("1.0.0"), List("Bot Doe"))
    val (nurtureAlg, pullRequestService) = nurtureAlgWithMockForge
    // the head that `findPullRequestForUpdate` looks for on GitHub
    val head =
      MockConfig.config.forgeCfg.tpe.pullRequestHeadFor(fixture.fork.repo, fixture.updateBranch)
    val buildFile = fixture.repoDir / "build.sbt"
    val program = for {
      pr <- pullRequestService.createPullRequest(
        fixture.updateData,
        NewPullRequestData.from(fixture.updateData, branchName = head)
      )
      _ <- pullRequestService.closePullRequest(fixture.repo, pr.number, closedReason)
      _ <- nurtureAlg.nurture(fixture.repoData, fixture.fork, Nel.one(update))
    } yield ()
    val initial = fixture.initialState
      .addFiles(buildFile -> """libraryDependencies += "com.example" % "thing-A" % "1.0.0"""")
      .unsafeRunSync()
    // enough of git to let nurture find, edit, commit and push the build file
    val state = initial.copy(commandOutputs =
      initial.commandOutputs ++ Map(
        Cmd.git(fixture.repoDir, "rev-parse", "--verify", "main") ->
          Right(List(dummySha1.value.value)),
        Cmd.gitGrep(fixture.repoDir, "1.0.0") -> Right(List("build.sbt")),
        Cmd.gitStatus(fixture.repoDir) -> Right(List(" M build.sbt")),
        Cmd.gitLatestSha1(fixture.repoDir) -> Right(List(dummySha1.value.value)),
        Cmd.git(fixture.repoDir, "diff", "--name-only", "main", fixture.updateBranch.name, "--") ->
          Right(List("build.sbt"))
      )
    )
    val (finalState, _) = fixture.withClone(program, state)
    val logs = finalState.trace.collect { case Log((_, msg)) => msg }.toList
    val prs = finalState.forgeState
      .repos(fixture.repo)
      .prs
      .values
      .map(p => (p.current.number, p.current.state))
      .toList
      .sortBy(_._1.value)
    (logs, prs)
  }

  test("nurture does not propose an update again whose PR was closed by somebody") {
    val (logs, prs) = nurtureWithClosedPr("declined", None)
    assert(logs.exists(_.endsWith("is closed")), logs)
    assertEquals(prs, List((PullRequestNumber(1), PullRequestState.Closed)))
  }

  test("nurture proposes an update again whose PR Steward closed as no longer needed") {
    val (logs, prs) = nurtureWithClosedPr("needed-again", Some(ClosedReason.AlreadyApplied))
    assert(logs.exists(_.contains("but the update is needed again")), logs)
    assertEquals(
      prs,
      List(
        (PullRequestNumber(1), PullRequestState.Closed),
        (PullRequestNumber(2), PullRequestState.Open)
      )
    )
  }
}
