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

  test("override applies for matching groupId, default for non-matching") {
    val config = CooldownConfig(
      minimumAge = 100.millis,
      overrides = Some(
        List(
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, None, None),
            minimumAge = 1.millis
          )
        )
      )
    )

    val matching =
      ("org.typelevel".g % "example".a % "1.0.0" %> VersionWithFirstSeen(
        "1.0.1".v,
        Some(Timestamp(0))
      )).single

    val nonMatching =
      ("org.other".g % "example".a % "1.0.0" %> VersionWithFirstSeen(
        "1.0.1".v,
        Some(Timestamp(0))
      )).single

    // matching: override age 1ms, version is 5ms old -> passes
    assertEquals(config.filterForAge(matching, Timestamp(5)), Some(matching))
    // non-matching: default age 100ms, version is 5ms old -> filtered out
    assertEquals(config.filterForAge(nonMatching, Timestamp(5)), None)
  }

  test("first matching override wins") {
    val config = CooldownConfig(
      minimumAge = 100.millis,
      overrides = Some(
        List(
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, Some("example"), None),
            minimumAge = 1.millis
          ),
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, None, None),
            minimumAge = 50.millis
          )
        )
      )
    )

    val update =
      ("org.typelevel".g % "example".a % "1.0.0" %> VersionWithFirstSeen(
        "1.0.1".v,
        Some(Timestamp(0))
      )).single

    // first override (1ms) wins, version is 5ms old -> passes
    assertEquals(config.filterForAge(update, Timestamp(5)), Some(update))
  }

  test("merge: repo overrides come before global, dedup applied") {
    val global = CooldownConfig(
      minimumAge = 7.days.toMillis.millis,
      overrides = Some(
        List(
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, None, None),
            minimumAge = 50.millis
          ),
          CooldownConfig.Override(
            UpdatePattern("org.shared".g, None, None),
            minimumAge = 60.millis
          )
        )
      )
    )
    val repo = CooldownConfig(
      minimumAge = 1.day.toMillis.millis,
      overrides = Some(
        List(
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, None, None),
            minimumAge = 1.millis
          )
        )
      )
    )

    val merged = UpdatesConfig.mergeCooldown(Some(global), Some(repo))

    assertEquals(
      merged.map(_.minimumAge),
      Some(1.day.toMillis.millis) // repo wins
    )
    assertEquals(
      merged.flatMap(_.overrides),
      Some(
        List(
          CooldownConfig.Override(
            UpdatePattern("org.typelevel".g, None, None),
            1.millis
          ),
          CooldownConfig.Override(
            UpdatePattern("org.shared".g, None, None),
            60.millis
          )
        )
      )
    )
  }

  test("merge: only one side defined") {
    val cfg = CooldownConfig(7.days.toMillis.millis)
    assertEquals(UpdatesConfig.mergeCooldown(Some(cfg), None), Some(cfg))
    assertEquals(UpdatesConfig.mergeCooldown(None, Some(cfg)), Some(cfg))
    assertEquals(UpdatesConfig.mergeCooldown(None, None), None)
  }
}
