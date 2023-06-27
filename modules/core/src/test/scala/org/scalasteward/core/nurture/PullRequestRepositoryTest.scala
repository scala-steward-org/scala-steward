package org.scalasteward.core.nurture

import cats.Id
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.forge.data.PullRequestState.Open
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.pullRequestRepository
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEff, MockState}
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
    val nextUpdate = portableScala.copy(newerVersions = Nel.of("1.0.1".v))

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
