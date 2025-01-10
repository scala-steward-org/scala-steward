package org.scalasteward.core.util

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*
import scala.collection.mutable.ListBuffer

class utilTest extends ScalaCheckSuite {
  test("appendBounded") {
    val lb = new ListBuffer[Int]
    lb.appendAll(List(1, 2, 3))

    assert(!appendBounded(lb, 4, 4))
    assertEquals(lb.toList, List(1, 2, 3, 4))

    assert(appendBounded(lb, 5, 4))
    assertEquals(lb.toList, List(3, 4, 5))

    assert(!appendBounded(lb, 6, 4))
    assertEquals(lb.toList, List(3, 4, 5, 6))

    assert(!appendBounded(lb, 7, 6))
    assertEquals(lb.toList, List(3, 4, 5, 6, 7))

    assert(!appendBounded(lb, 8, 6))
    assertEquals(lb.toList, List(3, 4, 5, 6, 7, 8))

    assert(appendBounded(lb, 9, 6))
    assertEquals(lb.toList, List(6, 7, 8, 9))
  }

  test("intersects") {
    assert(!intersects(List(1, 3, 5), Vector(2, 4, 6)))
    assert(intersects(List(1, 3, 5), Vector(2, 3, 6)))
  }

  test("takeUntilMaybe: with limit") {
    forAll(Gen.choose(0, 16)) { (n: Int) =>
      var count = 0
      val s = fs2.Stream.eval(IO(count += 1)).repeat.through(takeUntilMaybe(0, Some(n))(_ => 1))
      s.compile.drain.unsafeRunSync()
      assertEquals(count, n)
    }
  }

  test("takeUntilMaybe: without limit") {
    var count = 0
    val n = 3L
    val s = fs2.Stream.eval(IO(count += 1)).repeat.take(n).through(takeUntilMaybe(0, None)(_ => 0))
    s.compile.drain.unsafeRunSync()
    assertEquals(count, n.toInt)
  }
}
