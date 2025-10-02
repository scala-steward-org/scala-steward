package org.scalasteward.core.repoconfig

import cats.implicits.*
import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances.*

class PullRequestsConfigTest extends DisciplineSuite {
  checkAll("Monoid[PullRequestsConfig]", MonoidTests[PullRequestsConfig].monoid)

  test("global config to use 'draft' PRs should be retained after merging with local config") {
    val draftTrue = PullRequestsConfig(draft = Some(true))
    val draftUnset = PullRequestsConfig()
    val draftFalse = PullRequestsConfig(draft = Some(false))

    assert((draftTrue |+| draftUnset).draft === Some(true))
    assert((draftTrue |+| draftFalse).draft === Some(true))
    assert((draftUnset |+| draftUnset).draft === None)
  }
}
