package org.scalasteward.core.util

import cats.effect.IO
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._
import scala.collection.mutable.ListBuffer

class utilTest extends ScalaCheckSuite {
  test("appendBounded") {
    val lb = new ListBuffer[Int]
    lb.appendAll(List(1, 2, 3))

    appendBounded(lb, 4, 4)
    assertEquals(lb.toList, List(1, 2, 3, 4))

    appendBounded(lb, 5, 4)
    assertEquals(lb.toList, List(3, 4, 5))

    appendBounded(lb, 6, 4)
    assertEquals(lb.toList, List(3, 4, 5, 6))

    appendBounded(lb, 7, 6)
    assertEquals(lb.toList, List(3, 4, 5, 6, 7))

    appendBounded(lb, 8, 6)
    assertEquals(lb.toList, List(3, 4, 5, 6, 7, 8))

    appendBounded(lb, 9, 6)
    assertEquals(lb.toList, List(6, 7, 8, 9))
  }

  test("bindUntilTrue: empty list") {
    assertEquals(bindUntilTrue(List.empty[Option[Boolean]]), Some(false))
  }

  test("intersects") {
    assert(!intersects(List(1, 3, 5), Vector(2, 4, 6)))
    assert(intersects(List(1, 3, 5), Vector(2, 3, 6)))
  }

  test("takeUntil") {
    forAll(Gen.choose(0, 16)) { (n: Int) =>
      var count = 0
      val s = fs2.Stream.eval(IO(count += 1)).repeat.through(takeUntil(0, n)(_ => 1))
      s.compile.drain.unsafeRunSync()
      assertEquals(count, n)
    }
  }
}
