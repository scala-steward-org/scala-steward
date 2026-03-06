package org.scalasteward.core.repoconfig

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.coursier.VersionsCache.VersionWithFirstSeen
import org.scalasteward.core.util.Timestamp

import scala.concurrent.duration.*

class CooldownConfigTest extends FunSuite {
  test("apply first matching cooldown config") {
    val config = CooldownConfig(
      minimumAge = 7.millis
    )

    val update =
      ("org.bang".g % "example".a % "1.0.0" %> VersionWithFirstSeen(
        "1.0.1".v,
        Some(Timestamp(8))
      )).single

    assertEquals(
      config.filterForAge(update, Timestamp(10)),
      None
    )
  }
}
