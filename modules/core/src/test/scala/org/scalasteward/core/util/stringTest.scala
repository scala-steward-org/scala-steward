package org.scalasteward.core.util

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class stringTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  test("splitBetweenLowerAndUpperChars(s).mkString == s") {
    forAll(Gen.asciiStr) { s: String =>
      string.splitBetweenLowerAndUpperChars(s).mkString shouldBe s
    }
  }

  test(
    "splitBetweenLowerAndUpperChars: substrings end with lower case char or all are upper case"
  ) {
    forAll(Gen.asciiStr) { s: String =>
      string
        .splitBetweenLowerAndUpperChars(s)
        .forall(sub => sub.matches(".*\\p{javaLowerCase}") || sub.matches("\\p{javaUpperCase}*"))
    }
  }

  test("splitBetweenLowerAndUpperChars: examples") {
    string.splitBetweenLowerAndUpperChars("") shouldBe List("")
    string.splitBetweenLowerAndUpperChars("a") shouldBe List("a")
    string.splitBetweenLowerAndUpperChars("A") shouldBe List("A")
    string.splitBetweenLowerAndUpperChars("aa") shouldBe List("aa")
    string.splitBetweenLowerAndUpperChars("AA") shouldBe List("AA")
    string.splitBetweenLowerAndUpperChars("aA") shouldBe List("a", "A")
    string.splitBetweenLowerAndUpperChars("aAbB") shouldBe List("a", "Ab", "B")
  }
}
