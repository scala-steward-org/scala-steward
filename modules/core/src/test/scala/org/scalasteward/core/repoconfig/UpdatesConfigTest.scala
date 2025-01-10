package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.MonoidTests
import cats.syntax.all.*
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.data.GroupId

class UpdatesConfigTest extends DisciplineSuite {
  checkAll("Monoid[UpdatesConfig]", MonoidTests[UpdatesConfig].monoid)

  private val groupIdA = GroupId("A")
  private val groupIdB = GroupId("B")

  private val a00 = UpdatePattern(groupIdA, None, None)
  private val aa0 = UpdatePattern(groupIdA, Some("a"), None)
  private val ab0 = UpdatePattern(groupIdA, Some("b"), None)
  private val ac0 = UpdatePattern(groupIdA, Some("c"), None)

  private val aa1 = UpdatePattern(groupIdA, Some("a"), Some(VersionPattern(Some("1"))))
  private val aa2 = UpdatePattern(groupIdA, Some("a"), Some(VersionPattern(Some("2"))))
  private val ac3 = UpdatePattern(groupIdA, Some("c"), Some(VersionPattern(Some("3"))))

  private val b00 = UpdatePattern(groupIdB, None, None)

  test("mergePin: basic checks") {
    assertEquals(UpdatesConfig.mergePin(Nil.some, Nil.some), Nil.some)
    assertEquals(UpdatesConfig.mergePin(List(a00).some, Nil.some), List(a00).some)
    assertEquals(UpdatesConfig.mergePin(Nil.some, List(b00).some), List(b00).some)
    assertEquals(UpdatesConfig.mergePin(List(a00).some, List(b00).some), List(a00, b00).some)

    assertEquals(UpdatesConfig.mergePin(List(aa1).some, List(aa1, ac3).some), List(aa1, ac3).some)

    assertEquals(UpdatesConfig.mergePin(List(aa1).some, List(aa2).some), List(aa2).some)
    assertEquals(UpdatesConfig.mergePin(List(aa2).some, List(aa1).some), List(aa1).some)
  }

  test("mergePin: scala 3 LTS") {
    val groupIdScala = GroupId("org.scala-lang")
    val scala = Some("scala3-compiler")
    val s33 = UpdatePattern(groupIdScala, scala, Some(VersionPattern(Some("3.3."))))
    val s34 = UpdatePattern(groupIdScala, scala, Some(VersionPattern(Some("3.4."))))
    val s35 = UpdatePattern(groupIdScala, scala, Some(VersionPattern(Some("3.5."))))

    def mergeDefaultWithLocal(
        default: List[UpdatePattern],
        local: List[UpdatePattern]
    ): List[UpdatePattern] =
      UpdatesConfig.mergePin(default.some, local.some).getOrElse(Nil)

    assertEquals(mergeDefaultWithLocal(default = List(s33), local = Nil), List(s33))
    assertEquals(mergeDefaultWithLocal(default = List(s33), local = List(s34)), List(s34))
    assertEquals(mergeDefaultWithLocal(default = List(s33, s35), local = Nil), List(s33, s35))
    assertEquals(mergeDefaultWithLocal(default = List(s33, s35), local = List(s34)), List(s34))
    assertEquals(
      mergeDefaultWithLocal(default = List(s33, s35), local = List(s34, s35)),
      List(s34, s35)
    )
  }

  test("mergeAllow: basic checks") {
    assertEquals(UpdatesConfig.mergeAllow(Nil.some, Nil.some), Nil.some)
    assertEquals(UpdatesConfig.mergeAllow(List(a00).some, Nil.some), List(a00).some)
    assertEquals(UpdatesConfig.mergeAllow(Nil.some, List(a00).some), List(a00).some)

    assertEquals(UpdatesConfig.mergeAllow(List(a00).some, List(a00).some), List(a00).some)
    assertEquals(
      UpdatesConfig.mergeAllow(List(a00).some, List(b00).some),
      UpdatesConfig.nonExistingUpdatePattern.some
    )

    assertEquals(
      UpdatesConfig.mergeAllow(List(a00).some, List(aa1, ab0, ac3).some),
      List(aa1, ab0, ac3).some
    )
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa1, ab0, ac3).some, List(a00).some),
      List(aa1, ab0, ac3).some
    )

    assertEquals(UpdatesConfig.mergeAllow(List(aa0).some, List(aa1, aa2).some), List(aa1, aa2).some)
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, aa2).some, List(aa0).some), List(aa1, aa2).some)

    assertEquals(UpdatesConfig.mergeAllow(List(aa0).some, List(aa0, ab0).some), List(aa0).some)
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa0, ab0).some, List(aa0, ab0).some),
      List(aa0, ab0).some
    )

    assertEquals(UpdatesConfig.mergeAllow(List(aa1).some, List(aa1).some), List(aa1).some)
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa1).some, List(aa2).some),
      UpdatesConfig.nonExistingUpdatePattern.some
    )
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa1, ab0, ac0).some, List(aa2, ac0).some),
      List(ac0).some
    )
    assertEquals(
      UpdatesConfig.mergeAllow(List(aa1, aa2).some, List(aa1, aa2).some),
      List(aa1, aa2).some
    )
    assertEquals(UpdatesConfig.mergeAllow(List(aa1, aa2).some, List(aa1, ac3).some), List(aa1).some)
  }

  test("mergeIgnore: basic checks") {
    assertEquals(UpdatesConfig.mergeIgnore(Some(Nil), Some(Nil)), Some(Nil))
    assertEquals(UpdatesConfig.mergeIgnore(Some(List(a00)), Some(Nil)), Some(List(a00)))
    assertEquals(UpdatesConfig.mergeIgnore(Some(Nil), Some(List(b00))), Some(List(b00)))
    assertEquals(
      UpdatesConfig.mergeIgnore(Some(List(aa1, b00)), Some(List(aa1, aa2))),
      Some(List(aa1, b00, aa2))
    )
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
