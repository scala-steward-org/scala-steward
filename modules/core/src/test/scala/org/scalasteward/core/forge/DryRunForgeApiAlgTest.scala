package org.scalasteward.core.forge

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.{
  Comment,
  NewPullRequestData,
  PullRequestNumber,
  PullRequestState
}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockForgeApiAlg.{MockForgeState, RepoState}
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockForgeApiAlg, MockLogger, MockState}
import org.typelevel.log4cats.Logger

class DryRunForgeApiAlgTest extends FunSuite {
  private implicit val logger: Logger[MockEff] = new MockLogger
  private val underlying = MockForgeApiAlg.mockApiAlg
  private val dryRun = new DryRunForgeApiAlg[MockEff](underlying)

  private val repo = Repo("example-org", "example-repo")
  private val stateWithRepo: MockState =
    MockState.empty.copy(forgeState = MockForgeState(repos = Map(repo -> RepoState())))

  private val prData = NewPullRequestData(
    title = "Update foo to 1.2.3",
    body = "body",
    head = "update/foo-1.2.3",
    base = Branch("main"),
    labels = Nil,
    assignees = Nil,
    reviewers = Nil
  )

  test("createPullRequest does not open a PR, returns a placeholder, and logs") {
    val (finalState, pr) =
      dryRun.createPullRequest(repo, prData).runSA(stateWithRepo).unsafeRunSync()

    assertEquals(pr.number, PullRequestNumber(0))
    assertEquals(pr.state, PullRequestState.Open)
    assertEquals(pr.title, prData.title)
    // The forge state is untouched: no PR was created.
    assertEquals(finalState.forgeState.repos(repo), RepoState())
    assert(finalState.trace.exists {
      case Log((_, msg)) => msg.contains("[dry-run]")
      case _             => false
    })
  }

  test("updatePullRequest does not modify the forge state") {
    val finalState =
      dryRun.updatePullRequest(PullRequestNumber(1), repo, prData).runS(stateWithRepo).unsafeRunSync()

    assertEquals(finalState.forgeState.repos(repo), RepoState())
  }

  test("closePullRequest does not close the PR and reads it back") {
    val (finalState, (open, closeResult)) = (for {
      open <- underlying.createPullRequest(repo, prData)
      closeResult <- dryRun.closePullRequest(repo, open.number)
    } yield (open, closeResult)).runSA(stateWithRepo).unsafeRunSync()

    // The read-back returns the still-open PR ...
    assertEquals(closeResult.number, open.number)
    assertEquals(closeResult.state, PullRequestState.Open)
    // ... and it stays open in the forge state.
    assertEquals(
      finalState.forgeState.repos(repo).prs(open.number).current.state,
      PullRequestState.Open
    )
  }

  test("commentPullRequest does not post and echoes the comment") {
    val (finalState, comment) =
      dryRun.commentPullRequest(repo, PullRequestNumber(1), "hello").runSA(stateWithRepo).unsafeRunSync()

    assertEquals(comment, Comment("hello"))
    assertEquals(finalState.forgeState.repos(repo), RepoState())
  }

  test("getPullRequest delegates to the underlying forge") {
    val (created, fetched) = (for {
      created <- underlying.createPullRequest(repo, prData)
      fetched <- dryRun.getPullRequest(repo, created.number)
    } yield (created, fetched)).runA(stateWithRepo).unsafeRunSync()

    assertEquals(fetched, created)
  }
}
