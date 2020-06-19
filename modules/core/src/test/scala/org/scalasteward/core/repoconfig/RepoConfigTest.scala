package org.scalasteward.core.repoconfig

import cats.syntax.semigroup._
import org.scalasteward.core.data.GroupId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RepoConfigTest extends AnyFunSuite with Matchers {
  test("RepoConfig: semigroup") {
    RepoConfig.empty |+| RepoConfig.empty shouldBe RepoConfig.empty

    val cfg = RepoConfig(
      commits = CommitsConfig(Some("a")),
      pullRequests = PullRequestsConfig(Some(PullRequestFrequency.Asap)),
      updates = UpdatesConfig(
        allow =
          List(UpdatePattern(GroupId("A"), None, None), UpdatePattern(GroupId("B"), None, None))
      ),
      updatePullRequests = Some(PullRequestUpdateStrategy.Always)
    )

    cfg |+| RepoConfig.empty shouldBe cfg
    RepoConfig.empty |+| cfg shouldBe cfg
    cfg |+| cfg shouldBe cfg

    val cfg2 = RepoConfig(
      commits = CommitsConfig(Some("b")),
      pullRequests = PullRequestsConfig(Some(PullRequestFrequency.Daily)),
      updates = UpdatesConfig(
        allow =
          List(UpdatePattern(GroupId("B"), None, None), UpdatePattern(GroupId("C"), None, None))
      ),
      updatePullRequests = Some(PullRequestUpdateStrategy.OnConflicts)
    )

    cfg |+| cfg2 shouldBe cfg.copy(
      updates = UpdatesConfig(
        allow = List(UpdatePattern(GroupId("B"), None, None))
      )
    )

    cfg2 |+| cfg shouldBe cfg2.copy(
      updates = UpdatesConfig(
        allow = List(UpdatePattern(GroupId("B"), None, None))
      )
    )
  }
}
