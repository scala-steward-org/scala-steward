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
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.util.Nel

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
  private val branch = Branch("update")

  test("createOrUpdate >> findPullRequest >> lastPullRequestCreatedAt") {
    val repo = Repo("pr-repo-test", "repo1")
    val update = portableScala
    val data = PullRequestData[Id](url, sha1, update, Open, number, branch)

    val p = for {
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.findLatestPullRequest(repo, update.crossDependency, "1.0.0".v)
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
    val nextUpdate = portableScala.copy(newerVersions = Nel.of("1.0.1".v))
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

  test("findLatestPullRequest ignores grouped updates") {
    val repo = Repo("pr-repo-test", "repo5")
    val update = portableScala
    val grouped = Update.Grouped("group", None, List(update))
    val data = PullRequestData[Id](url, sha1, grouped, Open, number, branch)

    val p = for {
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      result <- pullRequestRepository.findLatestPullRequest(repo, update.crossDependency, "1.0.0".v)
    } yield result

    val (state, result) = p.runSA(MockState.empty).unsafeRunSync()

    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assert(result.isEmpty)

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString)
      )
    )
  }

  test("lastPullRequestCreatedAt returns timestamp for grouped updates") {
    val repo = Repo("pr-repo-test", "repo7")
    val update = catsCore
    val grouped = Update.Grouped("group", None, List(update))
    val data = PullRequestData[Id](url, sha1, grouped, Open, number, branch)

    val p = for {
      emptyCreatedAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
      _ <- pullRequestRepository.createOrUpdate(repo, data)
      createdAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
    } yield (emptyCreatedAt, createdAt)
    val (state, (emptyCreatedAt, createdAt)) = p.runSA(MockState.empty).unsafeRunSync()

    val store =
      config.workspace / s"store/pull_requests/v2/github/${repo.toPath}/pull_requests.json"
    assert(emptyCreatedAt.isEmpty)
    assert(createdAt.isDefined)

    checkTrace(
      state,
      Vector(
        Cmd("read", store.toString),
        Cmd("write", store.toString)
      )
    )
  }

}
