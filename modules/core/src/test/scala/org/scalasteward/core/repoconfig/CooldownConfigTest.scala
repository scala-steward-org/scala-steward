package org.scalasteward.core.repoconfig

import munit.FunSuite
import scala.concurrent.duration.*
import org.scalasteward.core.util.{Nel, Timestamp}
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.coursier.VersionsCache.VersionWithFirstSeen
import org.scalasteward.core.update.FilterAlg.TooYoungForCooldown

class CooldownConfigTest extends FunSuite {
  test("apply first matching cooldown config") {
    val config = CooldownConfig(
      minimumAge = 7.millis,
      artifacts = Nel.one(UpdatePattern(GroupId("*")))
    )

    val update2 =
      ("org.bang".g % "example".a % "1.0.0" %> VersionWithFirstSeen(
        "1.0.1".v,
        Some(Timestamp(8))
      )).single
    assertEquals(
      config.relevantConfigAppliedTo(update2, Timestamp(10)),
      Some(Left(TooYoungForCooldown(update2))) // Right(update2)
    )
  }
}
