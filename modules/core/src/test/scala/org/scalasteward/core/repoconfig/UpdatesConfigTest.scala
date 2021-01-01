package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.repoconfig.UpdatePattern.Version

class UpdatesConfigTest extends DisciplineSuite {
  checkAll("Monoid[UpdatesConfig]", MonoidTests[UpdatesConfig].monoid)

  private val groupIdA = GroupId("A")
  private val groupIdB = GroupId("B")

  private val a00 = UpdatePattern(groupIdA, None, None)
  private val aa0 = UpdatePattern(groupIdA, Some("a"), None)
  private val ab0 = UpdatePattern(groupIdA, Some("b"), None)
  private val ac0 = UpdatePattern(groupIdA, Some("c"), None)

  private val aa1 = UpdatePattern(groupIdA, Some("a"), Some(Version(Some("1"), None)))
  private val aa2 = UpdatePattern(groupIdA, Some("a"), Some(Version(Some("2"), None)))
  private val ac3 = UpdatePattern(groupIdA, Some("c"), Some(Version(Some("3"), None)))

  private val b00 = UpdatePattern(groupIdB, None, None)

  test("mergePin: basic checks") {
    assertEquals(UpdatesConfig.mergePin(Nil, Nil), Nil)
    assertEquals(UpdatesConfig.mergePin(List(a00), Nil), List(a00))
    assertEquals(UpdatesConfig.mergePin(Nil, List(b00)), List(b00))
    assertEquals(UpdatesConfig.mergePin(List(a00), List(b00)), List(a00, b00))

    assertEquals(UpdatesConfig.mergePin(List(aa1), List(aa1, ac3)), List(aa1, ac3))

    assertEquals(UpdatesConfig.mergePin(List(aa1), List(aa2)), List(aa1))
    assertEquals(UpdatesConfig.mergePin(List(aa2), List(aa1)), List(aa2))
  }

  test("mergeAllow: basic checks") {
    assertEquals(UpdatesConfig.mergeAllow(Nil, Nil), Nil)
    assertEquals(UpdatesConfig.mergeAllow(List(a00), Nil), List(a00))
    assertEquals(UpdatesConfig.mergeAllow(Nil, List(a00)), List(a00))

    assertEquals(UpdatesConfig.mergeAllow(List(a00), List(a00)), List(a00))
    assertEquals(
      UpdatesConfig.mergeAllow(List(a00), List(b00)),
      UpdatesConfig.nonExistingUpdatePattern
    )

    assertEquals(UpdatesConfig.mergeAllow(List(a00), List(aa1, ab0, ac3)), List(aa1, ab0, ac3))
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, ab0, ac3), List(a00)), List(aa1, ab0, ac3))

    assertEquals(UpdatesConfig.mergeAllow(List(aa0), List(aa1, aa2)), List(aa1, aa2))
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa0)), List(aa1, aa2))

    assertEquals(UpdatesConfig.mergeAllow(List(aa0), List(aa0, ab0)), List(aa0))
    assertEquals(UpdatesConfig.mergeAllow(List(aa0, ab0), List(aa0, ab0)), List(aa0, ab0))

    assertEquals(UpdatesConfig.mergeAllow(List(aa1), List(aa1)), List(aa1))
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa1), List(aa2)),
      UpdatesConfig.nonExistingUpdatePattern
    )
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, ab0, ac0), List(aa2, ac0)), List(ac0))
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa1, aa2)), List(aa1, aa2))
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa1, ac3)), List(aa1))
  }

  test("mergeIgnore: basic checks") {
    assertEquals(UpdatesConfig.mergeIgnore(Nil, Nil), Nil)
    assertEquals(UpdatesConfig.mergeIgnore(List(a00), Nil), List(a00))
    assertEquals(UpdatesConfig.mergeIgnore(Nil, List(b00)), List(b00))
    assertEquals(UpdatesConfig.mergeIgnore(List(aa1, b00), List(aa1, aa2)), List(aa1, b00, aa2))
  }

  test("mergeFileExtensions: basic checks") {
    assertEquals(UpdatesConfig.mergeFileExtensions(None, None), None)
    assertEquals(UpdatesConfig.mergeFileExtensions(None, Some(List("txt"))), Some(List("txt")))
    assertEquals(UpdatesConfig.mergeFileExtensions(Some(List("txt")), None), Some(List("txt")))
    assertEquals(
      UpdatesConfig.mergeFileExtensions(Some(List("txt")), Some(List("txt"))),
      Some(List("txt"))
    )
    assertEquals(
      UpdatesConfig.mergeFileExtensions(Some(List("a", "b", "c")), Some(List("b", "d"))),
      Some(List("b"))
    )
    assertEquals(UpdatesConfig.mergeFileExtensions(Some(List("a")), Some(List("b"))), Some(List()))
  }

  test("fileExtensionsOrDefault: non-set != empty") {
    assertNotEquals(
      UpdatesConfig(fileExtensions = None).fileExtensionsOrDefault,
      UpdatesConfig(fileExtensions = Some(Nil)).fileExtensionsOrDefault
    )
  }
}
