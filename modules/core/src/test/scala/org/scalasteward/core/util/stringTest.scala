package org.scalasteward.core.util

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._

class stringTest extends ScalaCheckSuite {
  property("splitBetweenLowerAndUpperChars(s).mkString == s") {
    forAll(Gen.asciiStr) { s: String =>
      assertEquals(string.splitBetweenLowerAndUpperChars(s).mkString, s)
    }
  }

  test("splitBetweenLowerAndUpperChars: examples") {
    assertEquals(string.splitBetweenLowerAndUpperChars(""), List(""))
    assertEquals(string.splitBetweenLowerAndUpperChars("a"), List("a"))
    assertEquals(string.splitBetweenLowerAndUpperChars("A"), List("A"))
    assertEquals(string.splitBetweenLowerAndUpperChars("aa"), List("aa"))
    assertEquals(string.splitBetweenLowerAndUpperChars("AA"), List("AA"))
    assertEquals(string.splitBetweenLowerAndUpperChars("aA"), List("a", "A"))
    assertEquals(string.splitBetweenLowerAndUpperChars("aAbB"), List("a", "Ab", "B"))
  }
}
