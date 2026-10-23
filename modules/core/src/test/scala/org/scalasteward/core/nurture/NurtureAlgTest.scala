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
import io.circe.Json
import io.circe.literal.*
import munit.CatsEffectSuite
import org.http4s.{HttpApp, Response}
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.{DependencyInfo, GroupId, Repo, RepoData, Update, UpdateData}
import org.scalasteward.core.edit.EditAttempt.UpdateEdit
import org.scalasteward.core.forge.data.NewPullRequestData
import org.scalasteward.core.git.{Branch, Commit}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.mock.{GitHubAuth, MockConfig, MockEff, MockEffOps, MockState}
import org.scalasteward.core.repoconfig.{
  ArtifactPattern,
  PullRequestsConfig,
  RepoConfig,
  VulnerabilityCheckConfig
}
import org.scalasteward.core.vulnerability.Vulnerability

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

  private val vulnerabilityCheckAlg =
    context(MockConfig.config.copy(enableVulnerabilityCheck = true)).nurtureAlg

  private val akkaHttpCore =
    "com.typesafe.akka".g % ("akka-http-core", "akka-http-core_2.13").a % "10.1.10"

  private val akkaHttpCoreQuery = json"""{
    "queries": [
      {
        "package": { "ecosystem": "Maven", "name": "com.typesafe.akka:akka-http-core_2.13" },
        "version": "10.1.10"
      },
      {
        "package": { "ecosystem": "Maven", "name": "com.typesafe.akka:akka-http-core_2.13" },
        "version": "10.2.10"
      }
    ]
  }"""

  private val akkaHttpCoreKey = ("com.typesafe.akka".g, "akka-http-core")

  private val noVulnerabilities = Map.empty[(GroupId, String), List[Vulnerability]]

  private val fixedVulnerability = Vulnerability(
    id = "GHSA-3hw2-h67c-wq66",
    summary = Some("Uncontrolled Recursion in Akka HTTP"),
    aliases = List("CVE-2021-42697"),
    severity = Some("HIGH")
  )

  private val akkaHttpCoreUpdate = (akkaHttpCore %> "10.2.10").single

  private val noExclusions = VulnerabilityCheckConfig()

  private def vulnerabilityUpdateData(update: Update): UpdateData = {
    val repoData =
      RepoData(Repo("scala-steward-org", "scala-steward"), dummyRepoCache, RepoConfig.empty)
    val updateBranch = Branch("update/akka-http-core-10.2.10")
    UpdateData(repoData, repoData.repo, update, Branch("main"), dummySha1, updateBranch)
  }

  private def osvState(
      queryBatchResponse: MockEff[Response[MockEff]],
      expectedQuery: Json = akkaHttpCoreQuery
  ): MockState =
    MockState.empty.copy(clientResponses = HttpApp[MockEff] {
      case request @ POST -> Root / "v1" / "querybatch" =>
        request.as[Json].flatMap { body =>
          if (body == expectedQuery) queryBatchResponse else BadRequest(body)
        }
      case GET -> Root / "v1" / "vulns" / "GHSA-3hw2-h67c-wq66" =>
        Ok(json"""{
          "id": "GHSA-3hw2-h67c-wq66",
          "summary": "Uncontrolled Recursion in Akka HTTP",
          "aliases": ["CVE-2021-42697"],
          "database_specific": { "severity": "HIGH" }
        }""")
      case _ => NotFound()
    })

  test("fixedVulnerabilities: advisory fixed by the next version") {
    val state = osvState(Ok(json"""{
      "results": [
        { "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }, { "id": "GHSA-qppj-fm5r-hxr3" }] },
        { "vulns": [{ "id": "GHSA-qppj-fm5r-hxr3" }] }
      ]
    }"""))
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(akkaHttpCoreUpdate, noExclusions)
    assertIO(obtained.runA(state), Map(akkaHttpCoreKey -> List(fixedVulnerability)))
  }

  private val pekkoMigrationQuery = json"""{
    "queries": [
      {
        "package": { "ecosystem": "Maven", "name": "com.typesafe.akka:akka-http-core_2.13" },
        "version": "10.1.10"
      },
      {
        "package": { "ecosystem": "Maven", "name": "org.apache.pekko:pekko-http-core_2.13" },
        "version": "1.0.1"
      }
    ]
  }"""

  private val pekkoMigrationUpdate = (akkaHttpCore %> "1.0.1").single
    .migration("org.apache.pekko".g.some, "pekko-http-core".some)

  test("fixedVulnerabilities: artifact migration queries the new artifact") {
    val state = osvState(
      Ok(json"""{ "results": [{ "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }] }, {}] }"""),
      pekkoMigrationQuery
    )
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(pekkoMigrationUpdate, noExclusions)
    assertIO(obtained.runA(state), Map(akkaHttpCoreKey -> List(fixedVulnerability)))
  }

  test("fixedVulnerabilities: artifact migration to an affected artifact fixes nothing") {
    val state = osvState(
      Ok(json"""{
        "results": [
          { "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }] },
          { "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }] }
        ]
      }"""),
      pekkoMigrationQuery
    )
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(pekkoMigrationUpdate, noExclusions)
    assertIO(obtained.runA(state), noVulnerabilities)
  }

  test("preparePullRequest with a fixed vulnerability") {
    val state = osvState(Ok(json"""{
      "results": [{ "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }] }, {}]
    }"""))
    val updateData = vulnerabilityUpdateData(akkaHttpCoreUpdate)
    val edits = List(UpdateEdit(updateData.update.asSingleUpdates.head, Commit(dummySha1)))
    vulnerabilityCheckAlg.preparePullRequest(updateData, edits).runA(state).map { obtained =>
      assert(clue(obtained.labels).contains("security-fix"))
      assert(
        clue(obtained.body).contains(
          """|⚠️ Fixes vulnerabilities:
             |  * [GHSA-3hw2-h67c-wq66](https://osv.dev/vulnerability/GHSA-3hw2-h67c-wq66) / CVE-2021-42697 (HIGH): Uncontrolled Recursion in Akka HTTP
             |""".stripMargin
        )
      )
    }
  }

  test("preparePullRequest ignores vulnerabilities of grouped updates without edits") {
    val akkaHttpCoreCurrentQuery = json"""{
      "package": { "ecosystem": "Maven", "name": "com.typesafe.akka:akka-http-core_2.13" },
      "version": "10.1.10"
    }"""
    val state = MockState.empty.copy(clientResponses = HttpApp[MockEff] {
      case request @ POST -> Root / "v1" / "querybatch" =>
        request.as[Json].flatMap { body =>
          val queries = body.hcursor.downField("queries").as[List[Json]].getOrElse(Nil)
          val results = queries.map { query =>
            if (query == akkaHttpCoreCurrentQuery)
              json"""{ "vulns": [{ "id": "GHSA-3hw2-h67c-wq66" }] }"""
            else Json.obj()
          }
          Ok(Json.obj("results" -> Json.fromValues(results)))
        }
      case GET -> Root / "v1" / "vulns" / "GHSA-3hw2-h67c-wq66" =>
        Ok(json"""{ "id": "GHSA-3hw2-h67c-wq66" }""")
      case _ => NotFound()
    })
    val catsEffectUpdate =
      ("org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.3.0" %> "3.4.0").single
    val update =
      Update.Grouped("all", None, List(akkaHttpCoreUpdate, catsEffectUpdate))
    val updateData = vulnerabilityUpdateData(update)
    val edits = List(UpdateEdit(catsEffectUpdate, Commit(dummySha1)))
    vulnerabilityCheckAlg.preparePullRequest(updateData, edits).runA(state).map { obtained =>
      assert(!clue(obtained.labels).contains("security-fix"))
    }
  }

  test("fixedVulnerabilities: none fixed") {
    val state = osvState(Ok(json"""{
      "results": [
        { "vulns": [{ "id": "GHSA-qppj-fm5r-hxr3" }] },
        { "vulns": [{ "id": "GHSA-qppj-fm5r-hxr3" }] }
      ]
    }"""))
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(akkaHttpCoreUpdate, noExclusions)
    assertIO(obtained.runA(state), noVulnerabilities)
  }

  test("fixedVulnerabilities: excluded artifact is not sent") {
    val exclusions =
      VulnerabilityCheckConfig(Some(List(ArtifactPattern("com.typesafe.*".g, None))))
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(akkaHttpCoreUpdate, exclusions)
    obtained.runSA(osvState(InternalServerError())).map { case (state, result) =>
      assertEquals(result, noVulnerabilities)
      assertEquals(state.trace, Vector.empty)
    }
  }

  test("fixedVulnerabilities: disabled by flag") {
    val obtained = context.nurtureAlg.fixedVulnerabilities(akkaHttpCoreUpdate, noExclusions)
    obtained.runSA(osvState(InternalServerError())).map { case (state, result) =>
      assertEquals(result, noVulnerabilities)
      assertEquals(state.trace, Vector.empty)
    }
  }

  test("fixedVulnerabilities: OSV failure is logged and ignored") {
    val obtained = vulnerabilityCheckAlg.fixedVulnerabilities(akkaHttpCoreUpdate, noExclusions)
    obtained.runSA(osvState(InternalServerError())).map { case (state, result) =>
      assertEquals(result, noVulnerabilities)
      assert(state.trace.exists {
        case Log((Some(_), "Vulnerability check failed")) => true
        case _                                            => false
      })
    }
  }
}
