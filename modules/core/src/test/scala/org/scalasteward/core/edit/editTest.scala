package org.scalasteward.core.edit

import cats.implicits._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class editTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  private val lineGen = Gen.frequency(
    3 -> Arbitrary.arbString.arbitrary,
    1 -> Gen.oneOf("scala-steward:off", "scala-steward:on"),
    1 -> Gen.alphaNumStr.map(_ + " // scala-steward:off")
  )

  private val contentGen = Gen.listOf(lineGen).map(_.mkString("\n"))

  test("splitByOffOnMarker") {
    forAll(contentGen) { s: String =>
      splitByOffOnMarker(s).foldMap { case (part, _) => part } shouldBe s
    }
  }
}
