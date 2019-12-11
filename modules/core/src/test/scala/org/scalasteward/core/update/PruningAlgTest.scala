package org.scalasteward.core.update

import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PruningAlgTest extends AnyFunSuite with Matchers {
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
      updates <- pruningAlg.checkForUpdates(List(repo))
    } yield updates

    result.runA(MockState.empty).unsafeRunSync() shouldBe expectedUpdates
  }
}
