package org.scalasteward.core.util

import cats.implicits._
import cats.kernel.laws.discipline.MonoidTests
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.util.Change._
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class ChangeTest extends FunSuite with Discipline {
  checkAll("Monoid[Change[T]]", MonoidTests[Change[String]].monoid)
}
