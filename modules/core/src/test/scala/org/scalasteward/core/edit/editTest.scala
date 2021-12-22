package org.scalasteward.core.edit

import cats.syntax.all._
import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}

class editTest extends ScalaCheckSuite {
  private val lineGen = Gen.frequency(
    3 -> Arbitrary.arbString.arbitrary,
    1 -> Gen.oneOf("scala-steward:off", "scala-steward:on"),
    1 -> Gen.alphaNumStr.map(_ + " // scala-steward:off")
  )

  private val contentGen = Gen.listOf(lineGen).map(_.mkString("\n"))

  test("splitByOffOnMarker") {
    forAll(contentGen) { s: String =>
      assertEquals(splitByOffOnMarker(s).foldMap { case (part, _) => part }, s)
    }
  }
}
