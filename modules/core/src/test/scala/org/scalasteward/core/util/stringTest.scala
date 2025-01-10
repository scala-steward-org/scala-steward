package org.scalasteward.core.util

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class stringTest extends ScalaCheckSuite {
  test("extractWords") {
    assertEquals(string.extractWords(""), List.empty)
    assertEquals(string.extractWords("abc"), List("abc"))
    assertEquals(string.extractWords("abcDEF"), List("abc", "DEF"))
    assertEquals(string.extractWords("abc-def"), List("abc", "def"))
    assertEquals(string.extractWords("abc_def"), List("abc", "def"))
    assertEquals(string.extractWords("abc.def"), List("abc", "def"))
    assertEquals(string.extractWords("abc.de"), List("abc"))
    assertEquals(string.extractWords("abc.defGHI"), List("abc", "def", "GHI"))
    assertEquals(string.extractWords("abDEF-xy.GHI"), List("DEF", "GHI"))
  }

  test("indentLines") {
    assertEquals(string.indentLines(List.empty[String]), "  ")
    assertEquals(string.indentLines(List("abc", "def", "ghi")), "  abc\n  def\n  ghi")
  }

  test("longestCommonPrefix") {
    assertEquals(string.longestCommonPrefix("", ""), "")
    assertEquals(string.longestCommonPrefix("abc", ""), "")
    assertEquals(string.longestCommonPrefix("abc", "abd"), "ab")
    assertEquals(string.longestCommonPrefix("abc", "zabc"), "")
    assertEquals(string.longestCommonPrefix("abc", "def"), "")
  }

  test("longestCommonPrefixGteq") {
    assertEquals(string.longestCommonPrefixGteq(Nel.of("abcde", "abchk"), 1), Some("abc"))
    assertEquals(string.longestCommonPrefixGteq(Nel.of("abcde", "abchk"), 3), Some("abc"))
    assertEquals(string.longestCommonPrefixGteq(Nel.of("abcde", "abhk"), 3), None)
  }

  test("rightmostLabel") {
    assertEquals(string.rightmostLabel("org.scalasteward.core"), "core")
    assertEquals(string.rightmostLabel("core"), "core")
    assertEquals(string.rightmostLabel(""), "")
  }

  test("lineLeftRight") {
    assertEquals(string.lineLeftRight("foo"), "──────────── foo ────────────")
    assertEquals(string.lineLeftRight(""), "────────────  ────────────")
  }

  property("splitBetweenLowerAndUpperChars(s).mkString == s") {
    forAll(Gen.asciiStr) { (s: String) =>
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
