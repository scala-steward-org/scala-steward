package org.scalasteward.core.util

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.util.Change._

class ChangeTest extends DisciplineSuite {
  checkAll("Monoid[Change[T]]", MonoidTests[Change[String]].monoid)
}
