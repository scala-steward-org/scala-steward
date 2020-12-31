package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.SemigroupTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class PullRequestsConfigTest extends DisciplineSuite {
  checkAll("Semigroup[PullRequestsConfig]", SemigroupTests[PullRequestsConfig].semigroup)
}
