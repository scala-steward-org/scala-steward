package org.scalasteward.core.nurture

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.{Dependency, Repo, RepoData, Update, UpdateData}
import org.scalasteward.core.forge.data.{NewPullRequestData, PullRequestOut}
import org.scalasteward.core.git.{branchFor, Branch, Sha1}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.MockForgeApiAlg.{MockForgeState, RepoState}
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockForgeApiAlg, MockState}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig

class PullRequestServiceTest extends FunSuite {

  val exampleRepo: Repo = Repo("example-org", "example-repo")
  val mockStateWithExampleRepo: MockState =
    MockState.empty.copy(forgeState = MockForgeState(repos = Map(exampleRepo -> RepoState())))
  val forgeApiAlg = MockForgeApiAlg.mockApiAlg
  val prRepository = context.pullRequestRepository
  val service: PullRequestService[MockEff] =
    new PullRequestService[MockEff](prRepository, forgeApiAlg)
  val dependencyA: Dependency = "com.example".g % "thing-A".a % "1.0.0"
  val dependencyB: Dependency = "com.example".g % "thing-B".a % "1.0.0"
  def createPrFor(update: UpdateData): MockEff[PullRequestOut] = service.createPullRequest(
    update,
    NewPullRequestData.from(update, branchName = update.updateBranch.name)
  )
  val baseSha = Sha1.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1")

  test("createPullRequest stores info in our local repository on the created PR") {
    val singleUpdate = (dependencyA %> "2.0.0").single
    val (createdPr, prFromRepository) = (for {
      createdPr <- createPrFor(updateDataFor(singleUpdate))
      prFromRepository <- prRepository.findLatestPullRequest(
        exampleRepo,
        singleUpdate.crossDependency,
        singleUpdate.nextVersion
      )
    } yield (createdPr, prFromRepository)).runA(mockStateWithExampleRepo).unsafeRunSync()

    assert(prFromRepository.flatMap(_.number).contains(createdPr.number))
    assert(prFromRepository.map(_.update).contains(singleUpdate))
  }

  test("getObsoleteOpenPullRequests does not return externally-closed PRs") {
    val (obsoleteOpenPrsA, obsoleteOpenPrsB) = (for {
      initialPrA <- createPrFor(updateDataFor((dependencyA %> "2.0.0").single))
      _ <- createPrFor(updateDataFor((dependencyB %> "2.0.0").single))
      _ <- forgeApiAlg.closePullRequest(exampleRepo, initialPrA.number)
      obsoleteOpenPrsA <- service.getObsoleteOpenPullRequests(
        exampleRepo,
        (dependencyA %> "3.0.0").single
      )
      obsoleteOpenPrsB <- service.getObsoleteOpenPullRequests(
        exampleRepo,
        (dependencyB %> "3.0.0").single
      )
    } yield (obsoleteOpenPrsA, obsoleteOpenPrsB)).runA(mockStateWithExampleRepo).unsafeRunSync()

    assertEquals(obsoleteOpenPrsA, List.empty)
    assertEquals(obsoleteOpenPrsB.size, 1)
  }

  private def updateDataFor(update: Update.Single) = UpdateData(
    repoData = RepoData(
      exampleRepo,
      cache = RepoCache(
        baseSha,
        dependencyInfos = List.empty,
        maybeRepoConfig = None,
        maybeRepoConfigParsingError = None
      ),
      config = RepoConfig()
    ),
    fork = exampleRepo,
    update,
    baseBranch = Branch("main"),
    baseSha1 = baseSha,
    updateBranch = branchFor(update)
  )
}
