package org.scalasteward.core.util

import cats.implicits._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.PosInt
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

class utilTest extends FunSuite with Matchers with PropertyChecks {
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

  test("halve: halves concatenated yields the original sequence") {
    forAll { l: List[Int] =>
      halve(l).fold(identity, { case (fst, snd) => fst ++ snd }) shouldBe l
    }
  }

  test("halve: first halve is at most one element longer than the second") {
    forAll { l: List[Int] =>
      val (fst, snd) = halve(l).leftMap(lst => (lst, List.empty[Int])).merge
      (fst.size - snd.size) should equal(0).or(equal(1))
    }
  }

  test("intersects") {
    intersects(List(1, 3, 5), Vector(2, 4, 6)) shouldBe false
    intersects(List(1, 3, 5), Vector(2, 3, 6)) shouldBe true
  }

  test("separateBy: retains elements") {
    forAll { (l: List[String], n: PosInt) =>
      separateBy(l)(n)(_.length).flatMap(_.toList).sorted shouldBe l.sorted
    }
  }

  test("separateBy: no duplicates in sublists") {
    forAll { (l: List[String], n: PosInt) =>
      separateBy(l)(n)(_.length).forall(_.groupBy(_.length).values.forall(_.size == 1))
    }
  }

  test("separateBy: sublists are not longer than maxSize") {
    forAll { (l: List[String], n: PosInt) =>
      separateBy(l)(n)(_.length).forall(_.length <= n.value)
    }
  }

  test("intersects") {
    intersects(List(1, 3, 5), List(2, 4, 6)) shouldBe false
    intersects(List(1, 3, 5), List(2, 3, 6)) shouldBe true
  }
}
