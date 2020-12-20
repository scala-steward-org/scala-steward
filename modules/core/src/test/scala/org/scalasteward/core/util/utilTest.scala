package org.scalasteward.core.util

import munit.FunSuite
import scala.collection.mutable.ListBuffer

class utilTest extends FunSuite {
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
}
