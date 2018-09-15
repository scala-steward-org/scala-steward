package eu.timepit.scalasteward

import cats.data.{NonEmptyList => Nel}
import eu.timepit.scalasteward.model.Update
import org.scalatest.{FunSuite, Matchers}

class sbtTest extends FunSuite with Matchers {
  test("sanitizeUpdates") {
    val update0 = Update("org.specs2", "specs2-core", "3.9.4", Nel.of("3.9.5"))
    val update1 = update0.copy(artifactId = "specs2-scalacheck")
    sbt.sanitizeUpdates(List(update0, update1)) shouldBe List(update0)
  }

  test("toUpdates") {
    val input = List(
      "[info] Found 1 dependency update for refined",
      "[info]   org.scala-lang:scala-library : 2.12.3 -> 2.12.6",
      "[info] Found 2 dependency updates for refined-scalacheck",
      "[info]   org.scala-lang:scala-library : 2.12.3 -> 2.12.6",
      "[info]   org.scalacheck:scalacheck    : 1.13.5           -> 1.14.0",
      "[info] Found 2 dependency updates for refined-pureconfig",
      "[info]   com.github.pureconfig:pureconfig : 0.8.0            -> 0.9.2",
      "[info]   org.scala-lang:scala-library     : 2.12.3 -> 2.12.6"
    )
    sbt.toUpdates(input) shouldBe List(
      Update("org.scala-lang", "scala-library", "2.12.3", Nel.of("2.12.6")),
      Update("org.scala-lang", "scala-library", "2.12.3", Nel.of("2.12.6")),
      Update("org.scalacheck", "scalacheck", "1.13.5", Nel.of("1.14.0")),
      Update("com.github.pureconfig", "pureconfig", "0.8.0", Nel.of("0.9.2")),
      Update("org.scala-lang", "scala-library", "2.12.3", Nel.of("2.12.6"))
    )
  }
}
