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

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.*
import org.scalasteward.core.forge.ForgeType.Bitbucket
import org.scalasteward.core.forge.data.{
  NewPullRequestData,
  PullRequestNumber,
  PullRequestOut,
  PullRequestState
}
import org.scalasteward.core.git.{branchFor, Branch, Sha1}
import org.scalasteward.core.mock.MockContext.context
import org.scalasteward.core.mock.MockForgeApiAlg.{MockForgeState, RepoState}
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockForgeApiAlg, MockState}
import org.scalasteward.core.nurture.TestRepo.{baseSha, withTestRepo}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.{RepoConfig, RetractedArtifact, UpdatePattern}
import org.scalasteward.core.util.Nel

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

  test("findPullRequestForUpdate() prefers an open PR over closed ones, whatever the order") {
    val singleUpdate = (dependencyA %> "2.0.0").single
    val (found, stored) = withTestRepo { testRepo =>
      val updateData = testRepo.updateDataFor(singleUpdate)
      val requestData = newPullRequestDataFor(updateData)
      for {
        first <- forgeApiAlg.createPullRequest(testRepo.repo, requestData)
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, first.number)
        _ <- forgeApiAlg.createPullRequest(testRepo.repo, requestData)
        third <- forgeApiAlg.createPullRequest(testRepo.repo, requestData)
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, third.number)
        found <- service.findPullRequestForUpdate(updateData, Bitbucket)
        stored <- prRepository.findLatestPullRequest(
          testRepo.repo,
          singleUpdate.crossDependency,
          singleUpdate.nextVersion
        )
      } yield (found.map(_.number), stored.flatMap(_.number))
    }
    assertEquals(found, Some(PullRequestNumber(2)))
    assertEquals(stored, Some(PullRequestNumber(2)))
  }

  test("findPullRequestForUpdate() falls back to the newest closed PR") {
    val singleUpdate = (dependencyA %> "2.0.0").single
    val found = withTestRepo { testRepo =>
      val updateData = testRepo.updateDataFor(singleUpdate)
      val requestData = newPullRequestDataFor(updateData)
      for {
        first <- forgeApiAlg.createPullRequest(testRepo.repo, requestData)
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, first.number)
        second <- forgeApiAlg.createPullRequest(testRepo.repo, requestData)
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, second.number)
        found <- service.findPullRequestForUpdate(updateData, Bitbucket)
      } yield found.map(pr => (pr.number, pr.state))
    }
    assertEquals(found, Some((PullRequestNumber(2), PullRequestState.Closed)))
  }

  test("findPullRequestForUpdate() stores info on the PR it finds in our local repository") {
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
        _ <- service.findPullRequestForUpdate(updateData, Bitbucket)
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

  test("findNoLongerNeededOpenPullRequests() does not return externally-closed PRs") {
    val updateA = (dependencyA %> "2.0.0").single
    val updateB = (dependencyB %> "2.0.0").single
    val baseB = dependencyB.copy(version = "2.1.0".v)
    val baseDependencies = List(dependencyA.copy(version = "2.0.0".v), baseB)
    val noLongerNeededOpenPrs = withTestRepo { testRepo =>
      for {
        prA <- createPrFor(testRepo.updateDataFor(updateA))
        _ <- createPrFor(testRepo.updateDataFor(updateB))
        _ <- forgeApiAlg.closePullRequest(testRepo.repo, prA.number)
        noLongerNeededOpenPrs <- service.findNoLongerNeededOpenPullRequests(
          testRepo.repo,
          baseDependencies,
          Nil,
          baseSha
        )
      } yield noLongerNeededOpenPrs
    }

    assertEquals(
      noLongerNeededOpenPrs.map { case (pr, reason) => (pr.update, reason) },
      List((updateB, NoLongerNeeded.AlreadyApplied(Nel.one(baseB))))
    )
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
