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

package org.scalasteward.core.update

import cats.effect.unsafe.implicits.global
import io.circe.parser.decode
import java.time.{Instant, LocalDateTime, ZoneOffset}
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Resolver.MavenRepository
import org.scalasteward.core.data.{DependencyInfo, Repo, RepoData, Scope, Update}
import org.http4s.syntax.literals.*
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git.{branchFor, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.{pruningAlg, pullRequestRepository}
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockEffOps, MockState}
import org.scalasteward.core.nurture.{ClosedReason, PullRequestData}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel

class PruningAlgTest extends FunSuite {
  test("needsAttention") {
    val repo = Repo("fthomas", "scalafix-test")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos": [],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v2/github/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val initial = MockState.empty.addFiles(pullRequestsFile -> pullRequestsContent).unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log("Find updates for fthomas/scalafix-test"),
        Log("Found 0 updates"),
        Cmd("read", pullRequestsFile.toString),
        Log("fthomas/scalafix-test is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: update scala-library") {
    val repo = Repo("pruning-test", "repo3")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "org.scala-lang",
          |            "artifactId" : {
          |              "name" : "scala-library",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.12.10",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion" : [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "updates" : {
          |      "includeScala" : "yes"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.9",
          |    "2.12.10",
          |    "2.12.11",
          |    "2.13.0",
          |    "2.13.1"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", pullRequestsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  org.scala-lang:scala-library : 2.12.10 -> 2.12.11"),
        Log(
          s"${repo.show} is outdated:\n  new version: org.scala-lang:scala-library : 2.12.10 -> 2.12.11"
        )
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: no overtaking updates") {
    val repo = Repo("pruning-test", "repo5")
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(
        Scope(
          List(
            DependencyInfo("org.scala-lang".g % "scala-library".a % "2.12.14", List("build.sbt")),
            DependencyInfo("org.scala-lang".g % "scala-library".a % "2.13.5", List("build.sbt"))
          ),
          List(MavenRepository("public", "https://repo5.org/maven/", None, None))
        )
      )
    )
    val data = RepoData(repo, repoCache, RepoConfig.empty)
    val versionsFile =
      config.workspace / "store/versions/v2/https/repo5.org/maven/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.13",
          |    "2.13.5"
          |  ]
          |}""".stripMargin
    (for {
      initial <- MockState.empty.addFiles(versionsFile -> versionsContent)
      // This should not propose an update from 2.12.13 to 2.13.5.
      updateStates <- pruningAlg.findUpdates(data).map(_.needingAttention).runA(initial)
    } yield assertEquals(updateStates, None)).unsafeRunSync()
  }

  /** Runs `findUpdates` for a repo with a single dependency whose only PR is closed, either by
    * somebody else or by Scala Steward because the update had already been applied.
    */
  private def needsAttentionWithClosedPr(
      repoName: String,
      closedReason: Option[ClosedReason]
  ): Option[Nel[Update]] = {
    val repo = Repo("pruning-test", repoName)
    val dependency = "com.example".g % "thing-a".a % "1.0.0"
    val update = (dependency %> "2.0.0").single
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(
        Scope(
          List(DependencyInfo(dependency, List("build.sbt"))),
          List(MavenRepository("public", s"https://$repoName.org/maven/", None, None))
        )
      )
    )
    val data = RepoData(repo, repoCache, RepoConfig.empty)
    val versionsFile =
      config.workspace / s"store/versions/v2/https/$repoName.org/maven/com/example/thing-a/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "1.0.0",
          |    "2.0.0"
          |  ]
          |}""".stripMargin
    val url = uri"https://github.com/pruning-test/pull/1"
    val closedPr = PullRequestData[cats.Id](
      url,
      Sha1.unsafeFrom("12def27a837ba6dc9e17406cbbe342fba3527c14"),
      update,
      PullRequestState.Open,
      PullRequestNumber(1),
      branchFor(update)
    )
    (for {
      initial <- MockState.empty.addFiles(versionsFile -> versionsContent)
      program =
        for {
          _ <- pullRequestRepository.createOrUpdate(repo, closedPr)
          _ <- pullRequestRepository.changeState(repo, url, PullRequestState.Closed, closedReason)
          found <- pruningAlg.findUpdates(data)
        } yield {
          // the update is found either way, whether it needs attention or not
          assertEquals(found.all.map(u => u: Update), List(update: Update))
          found.needingAttention.map(_.map(_.update))
        }
      result <- program.runA(initial)
    } yield result).unsafeRunSync()
  }

  test("needsAttention: an update whose PR was closed by somebody is not proposed again") {
    assertEquals(needsAttentionWithClosedPr("repo-closed-by-human", None), None)
  }

  test("needsAttention: an update whose PR Steward closed as already applied is proposed again") {
    val obtained =
      needsAttentionWithClosedPr("repo-closed-as-applied", Some(ClosedReason.AlreadyApplied))
    val update = ("com.example".g % "thing-a".a % "1.0.0" %> "2.0.0").single
    assertEquals(obtained, Some(Nel.one(update: Update)))
  }

  test("needsAttention: repo frequency (monthly) with zero frequencySpread") {
    val repo = Repo("pruning-test", "repo6")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@monthly",
          |      "frequencySpread": "0 days"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val timestampNow = Instant.now().minusSeconds(30).toEpochMilli
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : $timestampNow
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", pullRequestsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log("Ignoring outdated dependency software.awssdk:s3 for 29d 23h 59m"),
        Log(s"${repo.show} is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: allowedHours excluding the current hour") {
    val repo = Repo("pruning-test", "repo7")
    val blockedHour = (LocalDateTime.now(ZoneOffset.UTC).getHour + 12) % 24
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "allowedHours": "$blockedHour"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", pullRequestsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log(
          s"Ignoring outdated dependency software.awssdk:s3 according to allowed hours $blockedHour"
        ),
        Log(s"${repo.show} is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: allowedHours including the current hour") {
    val repo = Repo("pruning-test", "repo8")
    val hourNow = LocalDateTime.now(ZoneOffset.UTC).getHour
    val window = s"${(hourNow + 23) % 24},$hourNow,${(hourNow + 1) % 24}"
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "allowedHours": "$window"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", pullRequestsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log(
          s"${repo.show} is outdated:\n  new version: software.awssdk:s3 : 2.100.0 -> 2.300.0"
        )
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: group-specific frequency (monthly) vs repo frequency (asap)") {
    val repo = Repo("pruning-test", "repo3")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "dependencyOverrides": [
          |      {
          |        "dependency": { "groupId": "software.awssdk" },
          |        "pullRequests": { "frequency": "@monthly" }
          |      }
          |    ],
          |    "pullRequests": {
          |      "frequency": "@asap"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val timestampNow = Instant.now().toEpochMilli
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "ForArtifactId" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : $timestampNow
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", pullRequestsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log("Ignoring outdated dependency software.awssdk:s3 for 30d 21h 59m"),
        Log(s"${repo.show} is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: group-specific frequency (asap) vs repo frequency (monthly)") {
    val repo = Repo("pruning-test", "repo3")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "dependencyOverrides": [
          |      {
          |        "dependency": { "groupId": "software.awssdk" },
          |        "pullRequests": { "frequency": "@asap" }
          |      }
          |    ],
          |    "pullRequests": {
          |      "frequency": "@monthly"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log(
          "pruning-test/repo3 is outdated:\n  new version: software.awssdk:s3 : 2.100.0 -> 2.300.0"
        )
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: dependency can have artifactId and version") {
    val repo = Repo("pruning-test", "repo3")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion": [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/",
          |            "headers" : []
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "dependencyOverrides": [
          |      {
          |        "dependency": { "groupId": "software.awssdk", "artifactId": "ignored", "version": "2." },
          |        "pullRequests": { "frequency": "30 days" }
          |      },
          |      {
          |        "dependency": { "groupId": "software.awssdk", "artifactId": "s3", "version": "1." },
          |        "pullRequests": { "frequency": "4 days" }
          |      }
          |    ],
          |    "pullRequests": {
          |      "frequency": "@asap"
          |    }
          |  }
          |}""".stripMargin
    ): @unchecked
    val timestampNow = Instant.now().toEpochMilli
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.toPath}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "software.awssdk",
          |            "artifactId" : {
          |              "name" : "s3",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.100.0",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "2.200.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : $timestampNow
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/software/awssdk/s3/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.100.0",
          |    "2.200.0",
          |    "2.300.0"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.findUpdates(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log(s"Find updates for ${repo.show}"),
        Cmd("read", versionsFile.toString),
        Cmd("read", versionsFile.toString),
        Log("Found 1 update:\n  software.awssdk:s3 : 2.100.0 -> 2.300.0"),
        Log(
          s"${repo.show} is outdated:\n  new version: software.awssdk:s3 : 2.100.0 -> 2.300.0"
        )
      )
    )
    assertEquals(state, expected)
  }
}
