package org.scalasteward.core.nurture

import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{DependencyInfo, Repo, RepoData, UpdateData}
import org.scalasteward.core.edit.EditAttempt.UpdateEdit
import org.scalasteward.core.forge.data.NewPullRequestData
import org.scalasteward.core.git.{Branch, Commit}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.{MockConfig, MockEff, MockState}
import org.scalasteward.core.repoconfig.RepoConfig

class NurtureAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  test("preparePullRequest") {
    val nurtureAlg = context.nurtureAlg
    val repo = Repo("scala-steward-org", "scala-steward")
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0"
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(dependency, Nil)).withMavenCentral)
    )
    val repoData =
      RepoData(repo, repoCache, RepoConfig(assignees = List("foo"), reviewers = List("bar")))
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
             |</sup>""".stripMargin.trim,
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
    def nextToLast(L: Array[String]) = L(L.size - 2)

    val config =
      MockConfig.config.copy(forgeCfg = MockConfig.config.forgeCfg.copy(addLabels = false))
    val nurtureAlg = context(config).nurtureAlg
    val repo = Repo("scala-steward-org", "scala-steward")
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0"
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(List(DependencyInfo(dependency, Nil)).withMavenCentral)
    )
    val repoData =
      RepoData(repo, repoCache, RepoConfig(assignees = List("foo"), reviewers = List("bar")))
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
      val nextToLastLine = nextToLast(obtained.body.split("\n"))
      assertEquals(
        nextToLastLine,
        "labels: library-update, early-semver-minor, semver-spec-minor, version-scheme:early-semver, commit-count:1"
      )
    }
  }
}
