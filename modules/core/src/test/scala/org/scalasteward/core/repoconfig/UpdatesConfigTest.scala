package org.scalasteward.core.repoconfig

import eu.timepit.refined.types.numeric.PosInt
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.repoconfig.UpdatePattern.Version
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdatesConfigTest extends AnyFunSuite with Matchers {

  private val groupIdA = GroupId("A")
  private val groupIdB = GroupId("B")

  private val A00 = UpdatePattern(groupIdA, None, None)
  private val Aa0 = UpdatePattern(groupIdA, Some("a"), None)
  private val Ab0 = UpdatePattern(groupIdA, Some("b"), None)
  private val Ac0 = UpdatePattern(groupIdA, Some("c"), None)

  private val Aa1 = UpdatePattern(groupIdA, Some("a"), Some(Version(Some("1"), None)))
  private val Aa2 = UpdatePattern(groupIdA, Some("a"), Some(Version(Some("2"), None)))
  private val Ac3 = UpdatePattern(groupIdA, Some("c"), Some(Version(Some("3"), None)))

  private val B00 = UpdatePattern(groupIdB, None, None)

  test("semigroup: basic checks") {
    import cats.syntax.semigroup._

    val emptyCfg = UpdatesConfig()
    emptyCfg |+| emptyCfg shouldBe emptyCfg

    val cfg = UpdatesConfig(
      pin = List(Aa0),
      allow = List(Aa0),
      ignore = List(Aa0),
      limit = Some(PosInt(10)),
      includeScala = Some(false),
      fileExtensions = List(".txt", ".scala", ".sbt")
    )
    cfg |+| cfg shouldBe cfg

    cfg |+| emptyCfg shouldBe cfg
    emptyCfg |+| cfg shouldBe cfg

    val cfg2 = UpdatesConfig(
      pin = List(Ab0),
      allow = List(Ab0),
      ignore = List(Ab0),
      limit = Some(PosInt(20)),
      includeScala = Some(true),
      fileExtensions = List(".sbt", ".scala")
    )

    cfg |+| cfg2 shouldBe UpdatesConfig(
      pin = List(Aa0, Ab0),
      allow = UpdatesConfig.nonExistingUpdatePattern,
      ignore = List(Aa0, Ab0),
      limit = Some(PosInt(10)),
      includeScala = Some(false),
      fileExtensions = List(".scala", ".sbt")
    )

    cfg2 |+| cfg shouldBe UpdatesConfig(
      pin = List(Ab0, Aa0),
      allow = UpdatesConfig.nonExistingUpdatePattern,
      ignore = List(Ab0, Aa0),
      limit = Some(PosInt(20)),
      includeScala = Some(true),
      fileExtensions = List(".sbt", ".scala")
    )
  }

  test("mergePin: basic checks") {
    UpdatesConfig.mergePin(Nil, Nil) shouldBe  Nil
    UpdatesConfig.mergePin(List(A00), Nil) shouldBe List(A00)
    UpdatesConfig.mergePin(Nil, List(B00)) shouldBe List(B00)
    UpdatesConfig.mergePin(List(A00), List(B00)) shouldBe List(A00, B00)

    UpdatesConfig.mergePin(List(Aa1), List(Aa1, Ac3)) shouldBe List(Aa1, Ac3)

    UpdatesConfig.mergePin(List(Aa1), List(Aa2)) shouldBe List(Aa1)
    UpdatesConfig.mergePin(List(Aa2), List(Aa1)) shouldBe List(Aa2)
  }

  test("mergeAllow: basic checks") {
    UpdatesConfig.mergeAllow(Nil, Nil) shouldBe  Nil
    UpdatesConfig.mergeAllow(List(A00), Nil) shouldBe List(A00)
    UpdatesConfig.mergeAllow(Nil, List(A00)) shouldBe List(A00)

    UpdatesConfig.mergeAllow(List(A00), List(A00)) shouldBe List(A00)
    UpdatesConfig.mergeAllow(List(A00), List(B00)) shouldBe UpdatesConfig.nonExistingUpdatePattern

    UpdatesConfig.mergeAllow(List(A00), List(Aa1, Ab0, Ac3)) shouldBe List(Aa1, Ab0, Ac3)
    UpdatesConfig.mergeAllow(List(Aa1, Ab0, Ac3), List(A00)) shouldBe List(Aa1, Ab0, Ac3)

    UpdatesConfig.mergeAllow(List(Aa0), List(Aa1, Aa2)) shouldBe List(Aa1, Aa2)
    UpdatesConfig.mergeAllow(List(Aa1, Aa2), List(Aa0)) shouldBe List(Aa1, Aa2)

    UpdatesConfig.mergeAllow(List(Aa0), List(Aa0, Ab0)) shouldBe List(Aa0)
    UpdatesConfig.mergeAllow(List(Aa0, Ab0), List(Aa0, Ab0)) shouldBe List(Aa0, Ab0)

    UpdatesConfig.mergeAllow(List(Aa1), List(Aa1)) shouldBe List(Aa1)
    UpdatesConfig.mergeAllow(List(Aa1), List(Aa2)) shouldBe UpdatesConfig.nonExistingUpdatePattern
    UpdatesConfig.mergeAllow(List(Aa1, Ab0, Ac0), List(Aa2, Ac0)) shouldBe List(Ac0)
    UpdatesConfig.mergeAllow(List(Aa1, Aa2), List(Aa1, Aa2)) shouldBe List(Aa1, Aa2)
    UpdatesConfig.mergeAllow(List(Aa1, Aa2), List(Aa1, Ac3)) shouldBe List(Aa1)
  }

  test("mergeIgnore: basic checks") {
    UpdatesConfig.mergeIgnore(Nil, Nil) shouldBe Nil
    UpdatesConfig.mergeIgnore(List(A00), Nil) shouldBe List(A00)
    UpdatesConfig.mergeIgnore(Nil, List(B00)) shouldBe List(B00)
    UpdatesConfig.mergeIgnore(List(Aa1, B00), List(Aa1, Aa2)) shouldBe List(Aa1, B00, Aa2)
  }

  test("mergeFileExtensions: basic checks") {
    UpdatesConfig.mergeFileExtensions(Nil, Nil) shouldBe Nil
    UpdatesConfig.mergeFileExtensions(Nil, List("txt")) shouldBe List("txt")
    UpdatesConfig.mergeFileExtensions(List("txt"), Nil) shouldBe List("txt")
    UpdatesConfig.mergeFileExtensions(List("txt"), List("txt")) shouldBe List("txt")
    UpdatesConfig.mergeFileExtensions(List("a", "b", "c"), List("b", "d")) shouldBe List("b")
    UpdatesConfig.mergeFileExtensions(List("a"), List("b")) shouldBe UpdatesConfig.nonExistingFileExtension
  }
}
