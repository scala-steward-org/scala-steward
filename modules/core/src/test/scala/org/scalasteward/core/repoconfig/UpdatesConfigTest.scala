package org.scalasteward.core.repoconfig

import cats.syntax.semigroup._
import eu.timepit.refined.types.numeric.PosInt
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.repoconfig.UpdatePattern.Version
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdatesConfigTest extends AnyFunSuite with Matchers {

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

  test("semigroup: basic checks") {
    val emptyCfg = UpdatesConfig()
    emptyCfg |+| emptyCfg shouldBe emptyCfg

    val cfg = UpdatesConfig(
      pin = List(aa0),
      allow = List(aa0),
      ignore = List(aa0),
      limit = Some(PosInt(10)),
      includeScala = Some(false),
      fileExtensions = Some(List(".txt", ".scala", ".sbt"))
    )
    cfg |+| cfg shouldBe cfg

    cfg |+| emptyCfg shouldBe cfg
    emptyCfg |+| cfg shouldBe cfg

    val cfg2 = UpdatesConfig(
      pin = List(ab0),
      allow = List(ab0),
      ignore = List(ab0),
      limit = Some(PosInt(20)),
      includeScala = Some(true),
      fileExtensions = Some(List(".sbt", ".scala"))
    )

    cfg |+| cfg2 shouldBe UpdatesConfig(
      pin = List(aa0, ab0),
      allow = UpdatesConfig.nonExistingUpdatePattern,
      ignore = List(aa0, ab0),
      limit = Some(PosInt(10)),
      includeScala = Some(false),
      fileExtensions = Some(List(".scala", ".sbt"))
    )

    cfg2 |+| cfg shouldBe UpdatesConfig(
      pin = List(ab0, aa0),
      allow = UpdatesConfig.nonExistingUpdatePattern,
      ignore = List(ab0, aa0),
      limit = Some(PosInt(20)),
      includeScala = Some(true),
      fileExtensions = Some(List(".sbt", ".scala"))
    )
  }

  test("mergePin: basic checks") {
    UpdatesConfig.mergePin(Nil, Nil) shouldBe Nil
    UpdatesConfig.mergePin(List(a00), Nil) shouldBe List(a00)
    UpdatesConfig.mergePin(Nil, List(b00)) shouldBe List(b00)
    UpdatesConfig.mergePin(List(a00), List(b00)) shouldBe List(a00, b00)

    UpdatesConfig.mergePin(List(aa1), List(aa1, ac3)) shouldBe List(aa1, ac3)

    UpdatesConfig.mergePin(List(aa1), List(aa2)) shouldBe List(aa1)
    UpdatesConfig.mergePin(List(aa2), List(aa1)) shouldBe List(aa2)
  }

  test("mergeAllow: basic checks") {
    UpdatesConfig.mergeAllow(Nil, Nil) shouldBe Nil
    UpdatesConfig.mergeAllow(List(a00), Nil) shouldBe List(a00)
    UpdatesConfig.mergeAllow(Nil, List(a00)) shouldBe List(a00)

    UpdatesConfig.mergeAllow(List(a00), List(a00)) shouldBe List(a00)
    UpdatesConfig.mergeAllow(List(a00), List(b00)) shouldBe UpdatesConfig.nonExistingUpdatePattern

    UpdatesConfig.mergeAllow(List(a00), List(aa1, ab0, ac3)) shouldBe List(aa1, ab0, ac3)
    UpdatesConfig.mergeAllow(List(aa1, ab0, ac3), List(a00)) shouldBe List(aa1, ab0, ac3)

    UpdatesConfig.mergeAllow(List(aa0), List(aa1, aa2)) shouldBe List(aa1, aa2)
    UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa0)) shouldBe List(aa1, aa2)

    UpdatesConfig.mergeAllow(List(aa0), List(aa0, ab0)) shouldBe List(aa0)
    UpdatesConfig.mergeAllow(List(aa0, ab0), List(aa0, ab0)) shouldBe List(aa0, ab0)

    UpdatesConfig.mergeAllow(List(aa1), List(aa1)) shouldBe List(aa1)
    UpdatesConfig.mergeAllow(List(aa1), List(aa2)) shouldBe UpdatesConfig.nonExistingUpdatePattern
    UpdatesConfig.mergeAllow(List(aa1, ab0, ac0), List(aa2, ac0)) shouldBe List(ac0)
    UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa1, aa2)) shouldBe List(aa1, aa2)
    UpdatesConfig.mergeAllow(List(aa1, aa2), List(aa1, ac3)) shouldBe List(aa1)
  }

  test("mergeIgnore: basic checks") {
    UpdatesConfig.mergeIgnore(Nil, Nil) shouldBe Nil
    UpdatesConfig.mergeIgnore(List(a00), Nil) shouldBe List(a00)
    UpdatesConfig.mergeIgnore(Nil, List(b00)) shouldBe List(b00)
    UpdatesConfig.mergeIgnore(List(aa1, b00), List(aa1, aa2)) shouldBe List(aa1, b00, aa2)
  }

  test("mergeFileExtensions: basic checks") {
    UpdatesConfig.mergeFileExtensions(None, None) shouldBe None
    UpdatesConfig.mergeFileExtensions(None, Some(List("txt"))) shouldBe Some(List("txt"))
    UpdatesConfig.mergeFileExtensions(Some(List("txt")), None) shouldBe Some(List("txt"))
    UpdatesConfig.mergeFileExtensions(Some(List("txt")), Some(List("txt"))) shouldBe
      Some(List("txt"))
    UpdatesConfig.mergeFileExtensions(Some(List("a", "b", "c")), Some(List("b", "d"))) shouldBe
      Some(List("b"))
    UpdatesConfig.mergeFileExtensions(Some(List("a")), Some(List("b"))) shouldBe Some(List())
  }
}
