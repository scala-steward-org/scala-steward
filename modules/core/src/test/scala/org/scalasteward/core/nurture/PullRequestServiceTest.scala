package org.scalasteward.core.nurture

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.*
import org.scalasteward.core.forge.ForgeType.Bitbucket
import org.scalasteward.core.forge.data.{NewPullRequestData, PullRequestOut, PullRequestState}
import org.scalasteward.core.git.{branchFor, Branch, Sha1}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.MockForgeApiAlg.{MockForgeState, RepoState}
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockForgeApiAlg, MockState}
import org.scalasteward.core.nurture.TestRepo.{baseSha, withTestRepo}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.{RepoConfig, RetractedArtifact, UpdatePattern}

import java.util.concurrent.atomic.AtomicInteger

object TestRepo {
  val baseSha: Sha1 = Sha1.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1")

  val repoCount = new AtomicInteger()

  def withTestRepo[A](f: TestRepo => MockEff[A]): A = {
    val testRepo: TestRepo = TestRepo(
      Repo("example-org", s"example-repo-${repoCount.incrementAndGet()}")
    )
    f(testRepo)
      .runA(
        MockState.empty.copy(forgeState = MockForgeState(repos = Map(testRepo.repo -> RepoState())))
      )
      .unsafeRunSync()
  }
}

case class TestRepo(repo: Repo) {
  val mockStateWithExampleRepo: MockState =
    MockState.empty.copy(forgeState = MockForgeState(repos = Map(repo -> RepoState())))

  def updateDataFor(update: Update.Single) = UpdateData(
    repoData = RepoData(
      repo,
      cache = RepoCache(
        baseSha,
        dependencyInfos = List.empty,
        maybeRepoConfig = None,
        maybeRepoConfigParsingError = None
      ),
      config = RepoConfig()
    ),
    fork = repo,
    update,
    baseBranch = Branch("main"),
    baseSha1 = baseSha,
    updateBranch = branchFor(update)
  )
}

class PullRequestServiceTest extends FunSuite {

  // val exampleRepo: Repo = Repo("example-org", "example-repo")

  val forgeApiAlg = MockForgeApiAlg.mockApiAlg
  val prRepository = context.pullRequestRepository
  val service: PullRequestService[MockEff] =
    new PullRequestService[MockEff](prRepository, forgeApiAlg)
  val exampleGroupId: GroupId = "com.example".g
  val dependencyA: Dependency = exampleGroupId % "thing-A".a % "1.0.0"
  val dependencyB: Dependency = exampleGroupId % "thing-B".a % "1.0.0"
  def createPrFor(update: UpdateData): MockEff[PullRequestOut] =
    service.createPullRequest(update, newPullRequestDataFor(update))

  private def newPullRequestDataFor(update: UpdateData) =
    NewPullRequestData.from(update, branchName = update.updateBranch.name)

  test("createPullRequest() stores info in our local repository on the created PR") {
    val singleUpdate = (dependencyA %> "2.0.0").single
    val (createdPr, prFromRepository) = withTestRepo { testRepo =>
      for {
        createdPr <- createPrFor(testRepo.updateDataFor(singleUpdate))
        prFromRepository <- prRepository.findLatestPullRequest(
          testRepo.repo,
          singleUpdate.crossDependency,
          singleUpdate.nextVersion
        )
      } yield (createdPr, prFromRepository)
    }

    assert(prFromRepository.flatMap(_.number).contains(createdPr.number))
    assert(prFromRepository.map(_.update).contains(singleUpdate))
  }

  test("listPullRequestsForUpdate() stores info on the PRs it finds in our local repository") {
    val singleUpdate = (dependencyA %> "2.0.0").single

    val (before, after) = withTestRepo { testRepo =>
      val updateData = testRepo.updateDataFor(singleUpdate)
      val checkRepositoryForUpdatePR =
        prRepository.findLatestPullRequest(
          testRepo.repo,
          singleUpdate.crossDependency,
          singleUpdate.nextVersion
        )

      for {
        _ <- forgeApiAlg.createPullRequest(testRepo.repo, newPullRequestDataFor(updateData))
        before <- checkRepositoryForUpdatePR
        _ <- service.listPullRequestsForUpdate(updateData, Bitbucket)
        after <- checkRepositoryForUpdatePR
      } yield (before, after)
    }

    assertEquals(before.map(_.update), None)
    assertEquals(after.map(_.update), Some(singleUpdate))
  }

  test("getObsoleteOpenPullRequests() does not return externally-closed PRs") {
    val (obsoleteOpenPrsA, obsoleteOpenPrsB) = withTestRepo { testRepo =>
      def getObsoleteOpenPrsForUpdating(dependency: Dependency) =
        service.getObsoleteOpenPullRequests(testRepo.repo, (dependency %> "3.0.0").single)

      for {
        initialPrA <- createPrFor(testRepo.updateDataFor((dependencyA %> "2.0.0").single))
        _ <- createPrFor(testRepo.updateDataFor((dependencyB %> "2.0.0").single))
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, initialPrA.number)
        obsoleteOpenPrsA <- getObsoleteOpenPrsForUpdating(dependencyA)
        obsoleteOpenPrsB <- getObsoleteOpenPrsForUpdating(dependencyB)
      } yield (obsoleteOpenPrsA, obsoleteOpenPrsB)
    }

    assertEquals(obsoleteOpenPrsA, List.empty)
    assertEquals(obsoleteOpenPrsB.size, 1)
  }

  test("getRetractedOpenPullRequests() does not return externally-closed PRs") {
    val allRetractedArtifacts = List(
      RetractedArtifact(
        "example-reason",
        "example-doc",
        List(UpdatePattern(exampleGroupId, None, None))
      )
    )
    val updateA = (dependencyA %> "2.0.0").single
    val updateB = (dependencyB %> "2.0.0").single
    val retractedOpenPrs = withTestRepo { testRepo =>
      for {
        prA <- createPrFor(testRepo.updateDataFor(updateA))
        _ <- createPrFor(testRepo.updateDataFor(updateB))
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, prA.number)
        retractedOpenPrs <-
          service.getRetractedOpenPullRequests(testRepo.repo, allRetractedArtifacts)
      } yield retractedOpenPrs
    }

    assertEquals(retractedOpenPrs.map(_._1.update), List(updateB))
  }

  test("closePullRequest() updates state for the closed PR in our local repository") {
    val update = (dependencyA %> "2.0.0").single

    val foundPr = withTestRepo { testRepo =>
      for {
        createdPr <- createPrFor(testRepo.updateDataFor(update))
        _ <- service.closePullRequest(testRepo.repo, createdPr.number)
        foundPr <-
          prRepository.findLatestPullRequest(
            testRepo.repo,
            update.crossDependency,
            update.nextVersion
          )
      } yield foundPr
    }

    assertEquals(foundPr.map(_.state), Some(PullRequestState.Closed))
  }
}
