package org.scalasteward.core.nurture

import cats.Id
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.pullRequestRepository
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.PullRequestState.Open
import org.scalasteward.core.vcs.data.{PullRequestNumber, PullRequestState, Repo}

class PullRequestRepositoryTest extends FunSuite {
  private def checkTrace(state: MockState, trace: Vector[TraceEntry]): Unit =
    assertEquals(state.copy(files = Map.empty), MockState.empty.copy(trace = trace))

  private val portableScala =
    Update.Single("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1", Nel.of("1.0.0"))

  private val catsCore =
    Update.Single("org.typelevel" % "cats-core" % "1.0.0", Nel.of("1.0.1"))

  private val url = uri"https://github.com/typelevel/cats/pull/3291"
  private val sha1 = Sha1(HexString.unsafeFrom("a2ced5793c2832ada8c14ba5c77e51c4bc9656a8"))
  private val number = PullRequestNumber(3291)
  private val branch = Branch("update")

  test("createOrUpdate >> findPullRequest >> lastPullRequestCreatedAt") {
    val repo = Repo("pr-repo-test", "repo1")
    val update = portableScala
    val data = PullRequestData[Id](url, sha1, update, Open, number, branch)

    val p = for {
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.findLatestPullRequest(repo, update.crossDependency, "1.0.0")
      createdAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
    } yield (result, createdAt)
    val (state, (result, createdAt)) = p.runSA(MockState.empty).unsafeRunSync()

    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assertEquals(result.map(d => (d.url, d.baseSha1, d.state)), Some((url, sha1, Open)))
    assert(createdAt.isDefined)

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for single update") {
    val repo = Repo("pr-repo-test", "repo2")
    val update = portableScala
    val nextUpdate = portableScala.copy(newerVersions = Nel.of("1.0.1"))
    val data = PullRequestData[Id](url, sha1, update, Open, number, branch)

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
      _ <- pullRequestRepository.changeState(repo, url, PullRequestState.Closed)
      closedResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
    } yield (emptyResult, result, closedResult)
    val (state, (emptyResult, result, closedResult)) = p.runSA(MockState.empty).unsafeRunSync()
    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(closedResult, List.empty)
    assertEquals(result, List(data))

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString),
        Cmd("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for the same single update") {
    val repo = Repo("pr-repo-test", "repo3")
    val update = portableScala
    val data = PullRequestData[Id](url, sha1, update, Open, number, branch)

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, update)
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, update)
    } yield (emptyResult, result)
    val (state, (emptyResult, result)) = p.runSA(MockState.empty).unsafeRunSync()
    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(result, List.empty)

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for the another single update and ignore closed") {
    val repo = Repo("pr-repo-test", "repo4")
    val updateInStore = portableScala
    val newUpdate = catsCore
    val data = PullRequestData[Id](url, sha1, updateInStore, Open, number, branch)

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, updateInStore)
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, newUpdate)
    } yield (emptyResult, result)
    val (state, (emptyResult, result)) = p.runSA(MockState.empty).unsafeRunSync()
    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(result, List.empty)

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString)
      )
    )
  }
}
