package org.scalasteward.core.update

import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PruningAlgTest extends AnyFunSuite with Matchers {
  test("needsAttention") {
    val repo = Repo("fthomas", "scalafix-test")
    val repoCacheFile =
      config.workspace / "store/repo_cache/v1/fthomas/scalafix-test/repo_cache.json"
    val repoCacheContent =
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos": [],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v1/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
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
    val initial = MockState.empty
      .add(repoCacheFile, repoCacheContent)
      .add(pullRequestsFile, pullRequestsContent)
    val state = pruningAlg.needsAttention(repo).runS(initial).unsafeRunSync()

    state shouldBe initial.copy(
      commands = Vector(
        List("read", repoCacheFile.toString),
        List("read", pullRequestsFile.toString)
      ),
      logs = Vector(
        (None, "Find updates for fthomas/scalafix-test"),
        (None, "Found 0 updates"),
        (None, "fthomas/scalafix-test is up-to-date")
      )
    )
  }
}
