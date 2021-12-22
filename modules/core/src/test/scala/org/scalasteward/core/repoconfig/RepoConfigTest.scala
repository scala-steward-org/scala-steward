package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class RepoConfigTest extends DisciplineSuite {
  checkAll("Monoid[RepoConfig]", MonoidTests[RepoConfig].monoid)
}
