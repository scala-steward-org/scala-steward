package org.scalasteward.core.nurture

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockContext.context.pullRequestRepository
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.{PullRequestNumber, PullRequestState, Repo}

class PullRequestRepositoryTest extends FunSuite {
  private def checkCommands(state: MockState, commands: Vector[List[String]]): Unit =
    assertEquals(state.copy(files = Map.empty), MockState.empty.copy(commands = commands))

  private val url = uri"https://github.com/typelevel/cats/pull/3291"
  private val sha1 = Sha1(HexString.unsafeFrom("a2ced5793c2832ada8c14ba5c77e51c4bc9656a8"))
  private val number = PullRequestNumber(3291)

  test("createOrUpdate >> findPullRequest >> lastPullRequestCreatedAt") {
    val repo = Repo("pr-repo-test", "repo1")
    val update = TestData.Updates.PortableScala

    val p = for {
      _ <- pullRequestRepository.createOrUpdate(
        repo,
        url,
        sha1,
        update,
        PullRequestState.Open,
        number
      )
      result <- pullRequestRepository.findLatestPullRequest(repo, update.crossDependency, "1.0.0")
      createdAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
    } yield (result, createdAt)
    val (state, (result, createdAt)) = p.run(MockState.empty).unsafeRunSync()

    val store = config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    assertEquals(result, Some((url, sha1, PullRequestState.Open)))
    assert(createdAt.isDefined)

    checkCommands(
      state,
      Vector(
        List("read", store.toString),
        List("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for single update") {
    val repo = Repo("pr-repo-test", "repo2")
    val update = TestData.Updates.PortableScala
    val nextUpdate = TestData.Updates.PortableScala.copy(newerVersions = Nel.of("1.0.1"))

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
      _ <- pullRequestRepository.createOrUpdate(
        repo,
        url,
        sha1,
        update,
        PullRequestState.Open,
        number
      )
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
      _ <- pullRequestRepository.changeState(repo, url, PullRequestState.Closed)
      closedResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, nextUpdate)
    } yield (emptyResult, result, closedResult)
    val (state, (emptyResult, result, closedResult)) = p.run(MockState.empty).unsafeRunSync()
    val store = config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(closedResult, List.empty)
    assertEquals(result, List((number, url, TestData.Updates.PortableScala)))

    checkCommands(
      state,
      Vector(
        List("read", store.toString),
        List("write", store.toString),
        List("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for the same single update") {
    val repo = Repo("pr-repo-test", "repo3")
    val update = TestData.Updates.PortableScala

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, update)
      _ <- pullRequestRepository.createOrUpdate(
        repo,
        url,
        sha1,
        update,
        PullRequestState.Open,
        number
      )
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, update)
    } yield (emptyResult, result)
    val (state, (emptyResult, result)) = p.run(MockState.empty).unsafeRunSync()
    val store = config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(result, List.empty)

    checkCommands(
      state,
      Vector(
        List("read", store.toString),
        List("write", store.toString)
      )
    )
  }

  test("getObsoleteOpenPullRequests for the another single update and ignore closed") {
    val repo = Repo("pr-repo-test", "repo4")
    val updateInStore = TestData.Updates.PortableScala
    val newUpdate = TestData.Updates.CatsCore

    val p = for {
      emptyResult <- pullRequestRepository.getObsoleteOpenPullRequests(repo, updateInStore)
      _ <- pullRequestRepository.createOrUpdate(
        repo,
        url,
        sha1,
        updateInStore,
        PullRequestState.Open,
        number
      )
      result <- pullRequestRepository.getObsoleteOpenPullRequests(repo, newUpdate)
    } yield (emptyResult, result)
    val (state, (emptyResult, result)) = p.run(MockState.empty).unsafeRunSync()
    val store = config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    assertEquals(emptyResult, List.empty)
    assertEquals(result, List.empty)

    checkCommands(
      state,
      Vector(
        List("read", store.toString),
        List("write", store.toString)
      )
    )
  }
}

private[this] object TestData {
  object Updates {
    val PortableScala: Update.Single =
      Update.Single("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1", Nel.of("1.0.0"))

    val CatsCore: Update.Single =
      Update.Single("org.typelevel" % "cats-core" % "1.0.0", Nel.of("1.0.1"))

  }
}
