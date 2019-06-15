package org.scalasteward.core.model

import cats.implicits._
import cats.kernel.Comparison.{EqualTo, GreaterThan, LessThan}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers}

class VersionTest extends FunSuite with Matchers {
  val versions = Table(
    ("x", "y", "result"),
    ("1.0.0", "0.1", GreaterThan),
    ("1.8", "1.12", LessThan),
    ("1.2.3", "1.2.4", LessThan),
    ("1.2.3", "1.2.3", EqualTo),
    ("2.1", "2.1.3", LessThan),
    ("2.13.0-RC1", "2.13.0", LessThan),
    ("2.13.0-M2", "2.13.0", LessThan)
  )

  test("comparison") {
    forAll(versions) { (x, y, result) =>
      Version(x).comparison(Version(y)) shouldBe result
    }
  }
}
