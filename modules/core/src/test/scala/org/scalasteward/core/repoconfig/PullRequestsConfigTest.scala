package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class PullRequestsConfigTest extends DisciplineSuite {
  checkAll("Monoid[PullRequestsConfig]", MonoidTests[PullRequestsConfig].monoid)
}
