package org.scalasteward.core.update

import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {
  test("findUpdateUnderNewGroup: returns empty if dep is not listed") {
    val original = Dependency(GroupId("org.spire-math"), "UNKNOWN", "_2.12", "1.0.0")
    UpdateAlg.findUpdateUnderNewGroup(original) shouldBe None
  }

  test("findUpdateUnderNewGroup: returns Update.Single for updateing groupId") {
    val original = Dependency(GroupId("org.spire-math"), "kind-projector", "_2.12", "0.9.0")
    UpdateAlg.findUpdateUnderNewGroup(original) shouldBe Some(
      Update.Single(
        GroupId("org.spire-math"),
        "kind-projector",
        "0.9.0",
        Nel.of("0.10.0"),
        newerGroupId = Some(GroupId("org.typelevel"))
      )
    )
  }

  test("checkForUpdates: returns updates for artificialProject") {
    val repo = Repo("manuelcueto", "s3mock")
    val dep = List(Dependency(GroupId("org.typelevel"), "cats-core", "cats-core_2.12", "1.6.0"))
    val expectedUpdates = dep.map(_.toUpdate.copy(newerVersions = Nel.one("1.6.1")))
    val result = for {
      _ <- cacheRepository.updateCache(
        repo,
        RepoCache(
          Sha1(HexString.unsafeFrom("12345678ff12345678ff12345678ff12345678ff")),
          dep,
          None,
          None,
          None
        )
      )
      updates <- updateAlg.checkForUpdates(List(repo))
    } yield updates

    result.runA(MockState.empty).unsafeRunSync() shouldBe expectedUpdates
  }
}
