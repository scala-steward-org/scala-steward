package org.scalasteward.core.mock

import cats.Endo
import cats.data.Kleisli
import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.forge.data.PullRequestState.{Closed, Open}
import org.scalasteward.core.git.Branch

object MockForgeApiAlg {

  case class PRState(initialData: NewPullRequestData, current: PullRequestOut) {
    def close: PRState = copy(current = current.copy(state = Closed))

    def update(newData: NewPullRequestData): PRState =
      PRState(newData, current.copy(title = newData.title))
  }
  case class RepoState(highestPrNumber: Int = 0, prs: Map[PullRequestNumber, PRState] = Map.empty) {
    def createPullRequest(data: NewPullRequestData): (RepoState, PullRequestOut) = {
      val number = PullRequestNumber(highestPrNumber + 1)
      val pr = PullRequestOut(
        html_url = Uri.unsafeFromString(s"https://example.com/${number.value}"),
        state = Open,
        number,
        title = data.title
      )
      (copy(highestPrNumber = pr.number.value, prs = prs + (pr.number -> PRState(data, pr))), pr)
    }

    def getPullRequest(num: PullRequestNumber): PullRequestOut = prs(num).current

    def listPullRequests(head: String, base: Branch): List[PullRequestOut] = prs.values
      .filter(state => state.initialData.head == head && state.initialData.base == base)
      .map(_.current)
      .toList

    private def update(number: PullRequestNumber, f: Endo[PRState]): (RepoState, PullRequestOut) = {
      val state = copy(prs = prs.updatedWith(number)(_.map(f)))
      (state, state.getPullRequest(number))
    }

    def closePullRequest(number: PullRequestNumber): (RepoState, PullRequestOut) =
      update(number, _.close)

    def updatePullRequest(
        number: PullRequestNumber,
        data: NewPullRequestData
    ): (RepoState, PullRequestOut) =
      update(number, _.update(data))

  }

  case class MockForgeState(repos: Map[Repo, RepoState])

  object MockForgeState {
    val empty = MockForgeState(Map.empty)
  }

  implicit val mockApiAlg: ForgeApiAlg[MockEff] = new ForgeApiAlg[MockEff] {

    private def modifyRepo[A](repo: Repo, f: RepoState => (RepoState, A)): MockEff[A] = Kleisli {
      _.modify { state =>
        val (repoState, output) = f(state.forgeState.repos(repo))
        state.copy(forgeState =
          state.forgeState.copy(repos = state.forgeState.repos + (repo -> repoState))
        ) -> output
      }
    }

    private def readRepo[A](repo: Repo, f: RepoState => A): MockEff[A] =
      Kleisli(_.get.map(state => f(state.forgeState.repos(repo))))

    override def createPullRequest(repo: Repo, data: NewPullRequestData): MockEff[PullRequestOut] =
      modifyRepo(repo, _.createPullRequest(data))

    override def closePullRequest(repo: Repo, number: PullRequestNumber): MockEff[PullRequestOut] =
      modifyRepo(repo, _.closePullRequest(number))

    override def listPullRequests(
        repo: Repo,
        head: String,
        base: Branch
    ): MockEff[List[PullRequestOut]] = readRepo(repo, _.listPullRequests(head, base))

    override def getPullRequest(repo: Repo, number: PullRequestNumber): MockEff[PullRequestOut] =
      readRepo(repo, _.getPullRequest(number))

    override def createFork(repo: Repo): MockEff[RepoOut] = ???

    override def updatePullRequest(
        number: PullRequestNumber,
        repo: Repo,
        data: NewPullRequestData
    ): MockEff[Unit] = ???

    override def getBranch(repo: Repo, branch: Branch): MockEff[BranchOut] = ???

    override def getRepo(repo: Repo): MockEff[RepoOut] = ???

    override def commentPullRequest(
        repo: Repo,
        number: PullRequestNumber,
        comment: String
    ): MockEff[Comment] = ???
  }
}
