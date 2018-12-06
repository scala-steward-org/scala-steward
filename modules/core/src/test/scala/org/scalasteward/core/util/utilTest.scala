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
    val res = divideOnError(5) { i =>
      if (i <= 1) i.asRight[Unit] else ().asLeft[Int]
    } { i =>
      if (i > 1) List(i - 1, i - 2) else List.empty[Int]
    }((_, e: Unit) => Left(e))

    res shouldBe Right(5)
  }
}
