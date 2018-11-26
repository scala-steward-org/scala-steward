package eu.timepit.scalasteward

import eu.timepit.scalasteward.model.Update.{Group, Single}
import eu.timepit.scalasteward.util.Nel
import org.scalatest.{FunSuite, Matchers}

class NameResolverTest extends FunSuite with Matchers {

  test("resolve single update name: sttp:core") {
    val update = Single("com.softwaremill.sttp", "core", "1.3.3", Nel.one("1.3.5"))
    NameResolver.resolve(update) shouldBe "sttp:core"
  }

  test("resolve single update name: cats-core") {
    val update = Single("org.typelevel", "cats-core", "0.9.0", Nel.one("1.0.0"))
    NameResolver.resolve(update) shouldBe "cats-core"
  }

  test("resolve single update name: monix") {
    val update = Single("io.monix", "monix", "2.3.3", Nel.one("3.0.0"))
    NameResolver.resolve(update) shouldBe "monix"
  }

  test("resolve single update name: fs2-core") {
    val update = Single("co.fs2", "fs2-core", "0.9.7", Nel.one("1.0.0"))
    NameResolver.resolve(update) shouldBe "fs2-core"
  }

  test("resolve single update name: typesafe:config") {
    val update = Single("com.typesafe", "config", "1.3.0", Nel.one(" 1.3.3"))
    NameResolver.resolve(update) shouldBe "typesafe:config"
  }

  test("resolve group update name when the number of artifacts is less than 3") {
    val update = Group("org.typelevel", Nel.of("cats-core", "cats-free"), "0.9.0", Nel.one("1.0.0"))
    val expected = "cats-core, cats-free"
    NameResolver.resolve(update) shouldBe expected
  }

  test("resolve group update name when the number of artifacts is greater than 3") {
    val update = Group(
      "org.typelevel",
      Nel.of("cats-core", "cats-free", "cats-laws", "cats-macros"),
      "0.9.0",
      Nel.one("1.0.0")
    )
    val expected = "cats-core, cats-free, cats-laws..."
    NameResolver.resolve(update) shouldBe expected
  }

  test("resolve group update name when one artifact is a common suffix") {
    val update = Group(
      "com.softwaremill.sttp",
      Nel.of("circe", "core", "okhttp-backend-monix"),
      "1.3.3",
      Nel.one("1.3.5")
    )
    val expected = "sttp:circe, sttp:core, sttp:okhttp-backend-monix"
    NameResolver.resolve(update) shouldBe expected
  }
}
