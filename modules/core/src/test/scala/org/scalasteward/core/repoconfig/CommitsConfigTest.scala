package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class CommitsConfigTest extends DisciplineSuite {
  checkAll("Monoid[CommitsConfig]", MonoidTests[CommitsConfig].monoid)
}
