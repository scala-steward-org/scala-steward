package org.scalasteward.core.nurture

import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.mock.MockContext.{config, pullRequestRepository}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.{PullRequestState, Repo}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PullRequestRepositoryTest extends AnyFunSuite with Matchers {
  test("createOrUpdate >> findPullRequest >> lastPullRequestCreatedAt") {
    val repo = Repo("typelevel", "cats")
    val url = uri"https://github.com/typelevel/cats/pull/3291"
    val sha1 = Sha1(HexString("a2ced5793c2832ada8c14ba5c77e51c4bc9656a8"))
    val update =
      Update.Single("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1", Nel.of("1.0.0"))

    val p = for {
      _ <- pullRequestRepository.createOrUpdate(repo, url, sha1, update, PullRequestState.Open)
      result <- pullRequestRepository.findPullRequest(repo, update.crossDependency, "1.0.0")
      createdAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
    } yield (result, createdAt)
    val (state, (result, createdAt)) = p.run(MockState.empty).unsafeRunSync()

    val store = config.workspace / "store/pull_requests/v1/typelevel/cats/pull_requests.json"
    result shouldBe Some((url, sha1, PullRequestState.Open))
    createdAt.isDefined shouldBe true
    state.copy(files = Map.empty) shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", store.toString),
        List("write", store.toString),
        List("read", store.toString),
        List("read", store.toString)
      )
    )
  }
}
