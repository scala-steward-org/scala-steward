package org.scalasteward.core.repoconfig

import cats.syntax.semigroup._
import munit.FunSuite
import org.scalasteward.core.data.GroupId
import scala.concurrent.duration._

class RepoConfigTest extends FunSuite {
  test("RepoConfig: semigroup") {
    assertEquals(RepoConfig.empty |+| RepoConfig.empty, RepoConfig.empty)

    val cfg = RepoConfig(
      commits = CommitsConfig(Some("a")),
      pullRequests = PullRequestsConfig(Some(PullRequestFrequency.Asap)),
      updates = UpdatesConfig(
        allow =
          List(UpdatePattern(GroupId("A"), None, None), UpdatePattern(GroupId("B"), None, None))
      ),
      updatePullRequests = Some(PullRequestUpdateStrategy.Always)
    )

    assertEquals(cfg |+| RepoConfig.empty, cfg)
    assertEquals(RepoConfig.empty |+| cfg, cfg)
    assertEquals(cfg |+| cfg, cfg)

    val cfg2 = RepoConfig(
      commits = CommitsConfig(Some("b")),
      pullRequests = PullRequestsConfig(Some(PullRequestFrequency.Timespan(1.day))),
      updates = UpdatesConfig(
        allow =
          List(UpdatePattern(GroupId("B"), None, None), UpdatePattern(GroupId("C"), None, None))
      ),
      updatePullRequests = Some(PullRequestUpdateStrategy.OnConflicts)
    )

    assertEquals(
      cfg |+| cfg2,
      cfg.copy(updates = UpdatesConfig(allow = List(UpdatePattern(GroupId("B"), None, None))))
    )

    assertEquals(
      cfg2 |+| cfg,
      cfg2.copy(updates = UpdatesConfig(allow = List(UpdatePattern(GroupId("B"), None, None))))
    )
  }
}
