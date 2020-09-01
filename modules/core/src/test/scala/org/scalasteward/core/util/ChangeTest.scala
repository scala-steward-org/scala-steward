package org.scalasteward.core.util

import cats.implicits._
import cats.kernel.laws.discipline.MonoidTests
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.util.Change._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class ChangeTest extends AnyFunSuite with FunSuiteDiscipline with ScalaCheckPropertyChecks {
  checkAll("Monoid[Change[T]]", MonoidTests[Change[String]].monoid)
}
