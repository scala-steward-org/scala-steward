package org.scalasteward.core.util

import cats.implicits._
import org.scalatest.{FunSuite, Matchers}

class utilTest extends FunSuite with Matchers {
  test("divideOnError") {
    /*
       5
    => (4, 3)
    => ((3, 2), (2, 1))
    => (((2, 1), (1, 0)), ((1, 0), 1))
    => ((((1, 0), 1), (1, 0)), ((1, 0), 1))
     */
    divideOnError((i: Int) => if (i > 1) List(i - 1, i - 2) else Nil)(
      i => if (i <= 1) i.asRight[Unit] else ().asLeft[Int]
    )(5) shouldBe Right(5)
  }

  test("intersects") {
    intersects(List(1, 3, 5), List(2, 4, 6)) shouldBe false
    intersects(List(1, 3, 5), List(2, 3, 6)) shouldBe true
  }
}
