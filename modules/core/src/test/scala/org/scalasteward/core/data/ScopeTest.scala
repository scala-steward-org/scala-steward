package org.scalasteward.core.data

import cats.kernel.laws.discipline.OrderTests
import cats.laws.discipline.TraverseTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances.*

class ScopeTest extends DisciplineSuite {
  checkAll("Traverse[Scope]", TraverseTests[Scope].traverse[Int, Int, Int, Int, Option, Option])

  checkAll("Order[Scope[Int]]", OrderTests[Scope[Int]].order)
}
