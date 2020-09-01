package org.scalasteward.core.data
import cats.kernel.laws.discipline.OrderTests
import cats.laws.discipline.TraverseTests
import org.scalasteward.core.TestInstances._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class ScopeTest extends AnyFunSuite with FunSuiteDiscipline with ScalaCheckPropertyChecks {
  checkAll("Traverse[Scope]", TraverseTests[Scope].traverse[Int, Int, Int, Int, Option, Option])

  checkAll("Order[Scope[Int]]", OrderTests[Scope[Int]].order)
}
