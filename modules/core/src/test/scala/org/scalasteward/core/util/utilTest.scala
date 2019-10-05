package org.scalasteward.core.util

import cats.effect.IO
import cats.implicits._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.PosInt
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class utilTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  test("bindUntilTrue: empty list") {
    bindUntilTrue(List.empty[Option[Boolean]]) shouldBe Some(false)
  }

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

  test("evalFilter") {
    fs2.Stream
      .range[IO](1, 10)
      .through(evalFilter(i => IO(i % 2 == 0)))
      .compile
      .toList
      .unsafeRunSync() shouldBe List(2, 4, 6, 8)
  }

  test("halve: halves concatenated yields the original sequence") {
    forAll { l: List[Int] =>
      halve(l).fold(identity, { case (fst, snd) => fst ++ snd }) shouldBe l
    }
  }

  test("halve: first half is at most one element longer than the second") {
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
    forAll { (l: List[Char], n: PosInt, f: Char => Int) =>
      separateBy(l)(n)(f).flatMap(_.toList).sorted shouldBe l.sorted
    }
  }

  test("separateBy: no duplicates in sublists") {
    forAll { (l: List[Char], n: PosInt, f: Char => Int) =>
      separateBy(l)(n)(f).forall(_.groupBy(f).values.forall(_.size == 1)) shouldBe true
    }
  }

  test("separateBy: sublists are not longer than maxSize") {
    forAll { (l: List[Char], n: PosInt, f: Char => Int) =>
      separateBy(l)(n)(f).forall(_.length <= n.value) shouldBe true
    }
  }

  test("separateBy: sublists decrease in length") {
    forAll { (l: List[Char], n: PosInt, f: Char => Int) =>
      val lengths = separateBy(l)(n)(f).map(_.length)
      lengths.zip((lengths :+ 0).drop(1)).forall { case (l1, l2) => l1 >= l2 } shouldBe true
    }
  }

  test("separateBy: at least one sublist is densely packed") {
    forAll { (l: List[Char], n: PosInt, f: Char => Int) =>
      val maxSize = math.min(l.map(f).toSet.size, n.value)
      (l.isEmpty || separateBy(l)(n)(f).exists(_.size == maxSize)) shouldBe true
    }
  }
}
