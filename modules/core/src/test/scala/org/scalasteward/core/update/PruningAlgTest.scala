package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Scope
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PruningAlgTest extends AnyFunSuite with Matchers {
  test("extractOutOfSyncDependencies") {
    val da1 = Scope("ga" % "a1" % "1.a", List.empty)
    val da2 = Scope("ga" % "a2" % "1.a", List.empty)
    val da3 = Scope("ga" % "a3" % "0.a", List.empty)
    val da4 = Scope("ga" % "a4" % "1.a", List.empty)
    val db1 = Scope("gb" % "b1" % "1.b", List.empty)
    val db2 = Scope("gb" % "b2" % "1.b", List.empty)
    val dc1 = Scope("gc" % "c1" % "1.c", List.empty)

    val ua1 = Single(da1.value, Nel.of("3.a"))
    val ua4 = Single(da4.value, Nel.of("2.a"))
    val ub1 = Single(db1.value, Nel.of("2.b"))
    val ub2 = Single(db2.value, Nel.of("2.b"))
    val uc1 = Single(dc1.value, Nel.of("2.c"))

    val dependencies = List(da1, da2, da3, da4, db1, db2, dc1)
    val updates = List(ua1, ua4, ub1, ub2, uc1)

    val (actualDependencies, actualUpdates) =
      PruningAlg.extractOutOfSyncDependencies(dependencies, updates)
    actualDependencies shouldBe List(da1, da2, da4)
    actualUpdates shouldBe List(ub1, ub2, uc1)
  }
}
