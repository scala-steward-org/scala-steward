package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._

class ScalafmtConfigTest extends DisciplineSuite {
  checkAll("Monoid[ScalafmtConfig]", MonoidTests[ScalafmtConfig].monoid)
}
