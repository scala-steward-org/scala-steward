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

import cats.Id
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.NoLongerNeeded.{AlreadyApplied, DependencyRemoved}
import org.scalasteward.core.data.{Dependency, NoLongerNeeded, Repo, Update}
import org.scalasteward.core.forge.data.PullRequestState.Open
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.pullRequestRepository
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}
import org.scalasteward.core.repoconfig.{RetractedArtifact, UpdatePattern, VersionPattern}
import org.scalasteward.core.util.Nel

import java.util.concurrent.atomic.AtomicInteger

class PullRequestRepositoryTest extends FunSuite {
  private def checkTrace(state: MockState, trace: Vector[TraceEntry]): Unit =
    assertEquals(state.copy(files = Map.empty), MockState.empty.copy(trace = trace))

  private val portableScala =
    ("org.portable-scala".g % "sbt-scalajs-crossproject".a % "0.6.1" %> "1.0.0").single

  private val catsCore =
    ("org.typelevel".g % "cats-core".a % "1.0.0" %> "1.0.1").single

  private val url = uri"https://github.com/typelevel/cats/pull/3291"
  private val sha1 = Sha1.unsafeFrom("a2ced5793c2832ada8c14ba5c77e51c4bc9656a8")
  private val number = PullRequestNumber(3291)
  private def groupedUpdate(updates: Update.ForArtifactId*) =
    Update.Grouped("group", None, updates.toList)
  private def openPRFor(update: Update): PullRequestData[Id] =
    PullRequestData[Id](url, sha1, update, Open, number, Branch("update"))

  private val repoCounter = new AtomicInteger()

  private def executeOnTestRepo[T](expectedStoreOps: Seq[String])(p: Repo => MockEff[T]): T = {
    val repo = Repo("pr-repo-test", s"repo${repoCounter.getAndIncrement()}")
    val (state, output) = p(repo).runSA(MockState.empty).unsafeRunSync()

    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    checkTrace(
      state,
      expectedStoreOps.map(op => Cmd(op, store.toString)).toVector
    )

    output
  }

  private def beforeAndAfterPRCreation[T](update: Update)(func: Repo => MockEff[T]): (T, T) =
    executeOnTestRepo(expectedStoreOps = Seq("read", "write")) { repo =>
      for {
        before <- func(repo)
        _ <- pullRequestRepository.createOrUpdate(repo, openPRFor(update))
        after <- func(repo)
      } yield (before, after)
    }

  test("createOrUpdate >> findPullRequest >> lastPullRequestCreatedAt") {
    val update = portableScala
    val data = openPRFor(update)

    val (result, createdAt) = executeOnTestRepo(expectedStoreOps = Seq("read", "write")) { repo =>
      for {
        _ <- pullRequestRepository.createOrUpdate(repo, data)
        result <-
          pullRequestRepository.findLatestPullRequest(repo, update.crossDependency, "1.0.0".v)
        createdAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
      } yield (result, createdAt)
    }

    assertEquals(result.map(d => (d.url, d.baseSha1, d.state)), Some((url, sha1, Open)))
    assert(createdAt.isDefined)
  }

  test("getObsoleteOpenPullRequests for single update") {
    val data = openPRFor(portableScala)
    val nextUpdate = portableScala.copy(nextVersion = "1.0.1".v)

    val (emptyResult, result, closedResult) =
      executeOnTestRepo(expectedStoreOps = Seq("read", "write", "write")) { repo =>
        val getObsoleteOpenPRs = pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
        for {
          emptyResult <- getObsoleteOpenPRs
          _ <- pullRequestRepository.createOrUpdate(repo, data)
          result <- getObsoleteOpenPRs
          _ <- pullRequestRepository.changeState(repo, data.url, PullRequestState.Closed)
          closedResult <- getObsoleteOpenPRs
        } yield (emptyResult, result, closedResult)
      }

    assertEquals(emptyResult, List.empty)
    assertEquals(closedResult, List.empty)
    assertEquals(result, List(data))
  }

  test("getObsoleteOpenPullRequests for the same single update") {
    val (before, after) = beforeAndAfterPRCreation(portableScala) { repo =>
      pullRequestRepository.getObsoleteOpenPullRequests(repo, portableScala)
    }

    assertEquals(before, List.empty)
    assertEquals(after, List.empty)
  }

  test("getObsoleteOpenPullRequests for the another single update and ignore closed") {
    val (emptyResult, result) =
      executeOnTestRepo(expectedStoreOps = Seq("read", "write")) { repo =>
        for {
          emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, portableScala)
          _ <- pullRequestRepository.createOrUpdate(repo, openPRFor(portableScala))
          result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, catsCore)
        } yield (emptyResult, result)
      }

    assertEquals(emptyResult, List.empty)
    assertEquals(result, List.empty)
  }

  test("findNoLongerNeededOpenPullRequests reports a removed dependency after two refreshes") {
    val data = openPRFor(portableScala)
    val otherSha1 = Sha1.unsafeFrom("b2ced5793c2832ada8c14ba5c77e51c4bc9656a9")
    val present = "org.portable-scala".g % "sbt-scalajs-crossproject".a % "0.6.1"
    val (firstAbsent, sameShaAgain, backAgain, absentAgain, secondAbsent) =
      // writes: PR creation, first sighting, reset, first sighting again; a sighting at the same
      // sha and the final report change nothing and are not written
      executeOnTestRepo(expectedStoreOps = Seq("read", "write", "write", "write", "write")) {
        repo =>
          def check(dependencies: List[Dependency], sha1: Sha1) =
            pullRequestRepository.findNoLongerNeededOpenPullRequests(repo, dependencies, Nil, sha1)
          for {
            _ <- pullRequestRepository.createOrUpdate(repo, data)
            firstAbsent <- check(List.empty, sha1)
            sameShaAgain <- check(List.empty, sha1)
            // the dependency shows up again, which resets the sighting
            backAgain <- check(List(present), otherSha1)
            absentAgain <- check(List.empty, otherSha1)
            secondAbsent <- check(List.empty, sha1)
          } yield (firstAbsent, sameShaAgain, backAgain, absentAgain, secondAbsent)
      }
    assertEquals(firstAbsent, List.empty)
    assertEquals(sameShaAgain, List.empty)
    assertEquals(backAgain, List.empty)
    assertEquals(absentAgain, List.empty)
    assertEquals(secondAbsent, List((data, DependencyRemoved)))
  }

  test("Entry decodes store files written before closedReason and absentSince existed") {
    val json =
      s"""|{
          |  "baseSha1" : "${sha1.value.value}",
          |  "update" : ${Update.updateEncoder(portableScala).noSpaces},
          |  "state" : "open",
          |  "entryCreatedAt" : 1581969227183,
          |  "number" : 3291,
          |  "updateBranch" : "update"
          |}""".stripMargin
    val obtained = io.circe.parser.decode[PullRequestRepository.Entry](json)
    assertEquals(obtained.map(e => (e.closedReason, e.absentSince)), Right((None, None)))
    assertEquals(obtained.map(_.update), Right(portableScala: Update))
  }

  test("findNoLongerNeededOpenPullRequests ignores closed PRs") {
    val data = openPRFor(portableScala)
    val dependency = "org.portable-scala".g % "sbt-scalajs-crossproject".a % "1.0.0"
    val (result, closedResult) =
      executeOnTestRepo(expectedStoreOps = Seq("read", "write", "write")) { repo =>
        val getNoLongerNeededOpenPRs =
          pullRequestRepository.findNoLongerNeededOpenPullRequests(
            repo,
            List(dependency),
            Nil,
            sha1
          )
        for {
          _ <- pullRequestRepository.createOrUpdate(repo, data)
          result <- getNoLongerNeededOpenPRs
          _ <- pullRequestRepository.changeState(repo, data.url, PullRequestState.Closed)
          closedResult <- getNoLongerNeededOpenPRs
        } yield (result, closedResult)
      }
    assertEquals(result, List((data, AlreadyApplied(Nel.one(dependency)))))
    assertEquals(closedResult, List.empty)
  }

  test("findNoLongerNeededOpenPullRequests ignores grouped updates") {
    val dependency = "org.portable-scala".g % "sbt-scalajs-crossproject".a % "1.0.0"
    val (_, obtained) = beforeAndAfterPRCreation(groupedUpdate(portableScala)) { repo =>
      pullRequestRepository.findNoLongerNeededOpenPullRequests(repo, List(dependency), Nil, sha1)
    }
    assertEquals(obtained, List.empty[(PullRequestData[Id], NoLongerNeeded)])
  }

  test("wasClosedAsNoLongerNeeded is only true for PRs closed with that reason") {
    val data = openPRFor(portableScala)
    val (open, closedByHuman, closedAsNoLongerNeeded, afterListing, reopened) =
      executeOnTestRepo(expectedStoreOps =
        Seq("read", "write", "write", "write", "write", "write")
      ) { repo =>
        val wasClosedAsNoLongerNeeded =
          pullRequestRepository.wasClosedAsNoLongerNeeded(repo, data.url)
        for {
          _ <- pullRequestRepository.createOrUpdate(repo, data)
          open <- wasClosedAsNoLongerNeeded
          _ <- pullRequestRepository.changeState(repo, data.url, PullRequestState.Closed)
          closedByHuman <- wasClosedAsNoLongerNeeded
          _ <- pullRequestRepository.changeState(
            repo,
            data.url,
            PullRequestState.Closed,
            Some(ClosedReason.AlreadyApplied)
          )
          closedAsNoLongerNeeded <- wasClosedAsNoLongerNeeded
          // listing the PR again refreshes the entry but must keep the reason
          _ <- pullRequestRepository
            .createOrUpdate(repo, data.copy(state = PullRequestState.Closed))
          afterListing <- wasClosedAsNoLongerNeeded
          _ <- pullRequestRepository.changeState(repo, data.url, PullRequestState.Open)
          reopened <- wasClosedAsNoLongerNeeded
        } yield (open, closedByHuman, closedAsNoLongerNeeded, afterListing, reopened)
      }
    assertEquals(open, false)
    assertEquals(closedByHuman, false)
    assertEquals(closedAsNoLongerNeeded, true)
    assertEquals(afterListing, true)
    assertEquals(reopened, false)
  }

  test("getRetractedPullRequests with no retractions defined") {
    val (_, obtained) = beforeAndAfterPRCreation(portableScala) { repo =>
      pullRequestRepository.getRetractedOpenPullRequests(repo, List.empty)
    }
    assertEquals(obtained, List.empty[(PullRequestData[Id], RetractedArtifact)])
  }

  test("getRetractedPullRequests with retractions") {
    val retractedPortableScala = RetractedArtifact(
      "a reason",
      "doc URI",
      List(
        UpdatePattern(
          "org.portable-scala".g,
          Some("sbt-scalajs-crossproject"),
          Some(VersionPattern(exact = Some("1.0.0")))
        )
      )
    )
    val (_, obtained) = beforeAndAfterPRCreation(portableScala) { repo =>
      pullRequestRepository.getRetractedOpenPullRequests(repo, List(retractedPortableScala))
    }
    assertEquals(obtained.size, 1)
    assertEquals(obtained.head._1.update, portableScala)
    assertEquals(obtained.head._2, retractedPortableScala)
  }

  test("getRetractedPullRequests with retractions for different version") {
    val retractedPortableScala = RetractedArtifact(
      "a reason",
      "doc URI",
      List(
        UpdatePattern(
          "org.portable-scala".g,
          Some("sbt-scalajs-crossproject"),
          Some(VersionPattern(exact = Some("2.0.0")))
        )
      )
    )
    val (_, obtained) = beforeAndAfterPRCreation(portableScala) { repo =>
      pullRequestRepository.getRetractedOpenPullRequests(repo, List(retractedPortableScala))
    }
    assertEquals(obtained, List.empty[(PullRequestData[Id], RetractedArtifact)])
  }

  test("findLatestPullRequest ignores grouped updates") {
    val (_, result) = beforeAndAfterPRCreation(groupedUpdate(portableScala)) { repo =>
      pullRequestRepository.findLatestPullRequest(repo, portableScala.crossDependency, "1.0.0".v)
    }
    assert(result.isEmpty)
  }

  test("lastPullRequestCreatedAt returns timestamp for grouped updates") {
    val (before, after) =
      beforeAndAfterPRCreation(groupedUpdate(catsCore))(
        pullRequestRepository.lastPullRequestCreatedAt
      )
    assert(before.isEmpty)
    assert(after.isDefined)
  }

  test("lastPullRequestCreatedAtByArtifact for grouped updates") {
    val (before, after) =
      beforeAndAfterPRCreation(groupedUpdate(catsCore))(
        pullRequestRepository.lastPullRequestCreatedAtByArtifact
      )
    assert(before.isEmpty)
    assert(after.contains(catsCore.groupAndMainArtifactId))
  }
}
