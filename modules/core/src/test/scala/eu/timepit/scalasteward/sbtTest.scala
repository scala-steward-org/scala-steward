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
}
