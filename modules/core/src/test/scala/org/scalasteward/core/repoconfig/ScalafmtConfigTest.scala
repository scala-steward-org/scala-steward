package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.SemigroupTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class ScalafmtConfigTest extends DisciplineSuite {
  checkAll("Semigroup[ScalafmtConfig]", SemigroupTests[ScalafmtConfig].semigroup)
}
