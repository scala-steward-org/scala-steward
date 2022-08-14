package org.scalasteward.core.update

import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.mock.MockContext.context.filterAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.repoconfig._
import org.scalasteward.core.update.FilterAlg._
import org.scalasteward.core.util.Nel

class FilterAlgTest extends FunSuite {
  private val config = RepoConfig.empty

  test("localFilter: SNAP -> SNAP") {
    val update =
      ("org.scalatest".g % "scalatest".a % "3.0.8-SNAP2" %> Nel.of("3.0.8-SNAP10")).single
    assertEquals(localFilter(update, config), Right(update))
  }

  test("localFilter: RC -> SNAP") {
    val update = ("org.scalatest".g % "scalatest".a % "3.0.8-RC2" %> Nel.of("3.1.0-SNAP10")).single
    assertEquals(localFilter(update, config), Left(NoSuitableNextVersion(update)))
  }

  test("localFilter: update without bad version") {
    val update = ("com.jsuereth".g % "sbt-pgp".a % "1.1.0" %> Nel.of("1.1.2", "2.0.0")).single
    assertEquals(localFilter(update, config), Right(update.copy(newerVersions = Nel.of("1.1.2".v))))
  }

  test("localFilter: update with bad version") {
    val update = ("com.jsuereth".g % "sbt-pgp".a % "1.1.2-1" %> Nel.of("1.1.2", "2.0.0")).single
    assertEquals(localFilter(update, config), Right(update.copy(newerVersions = Nel.of("2.0.0".v))))
  }

  test("localFilter: update with bad version 2") {
    val update = ("net.sourceforge.plantuml".g % "plantuml".a % "1.2019.11" %>
      Nel.of("7726", "8020", "2017.09", "1.2019.12")).single
    assertEquals(
      localFilter(update, config),
      Right(update.copy(newerVersions = Nel.of("1.2019.12".v)))
    )
  }

  test("localFilter: update to pre-releases of a different series") {
    val update = ("com.jsuereth".g % "sbt-pgp".a % "1.1.2-1" %> Nel.of("2.0.1-M3")).single
    assertEquals(localFilter(update, config), Left(NoSuitableNextVersion(update)))
  }

  test("localFilter: allowed update to pre-releases of a different series") {
    val update = ("com.jsuereth".g % "sbt-pgp".a % "1.1.2-1" %> Nel.of("2.0.1-M3")).single
    val allowedPreReleases =
      UpdatePattern("com.jsuereth".g, Some("sbt-pgp"), None) :: config.updates.allowPreReleases
    val configWithAllowed =
      config.copy(updates = config.updates.copy(allowPreReleases = allowedPreReleases))

    val expected = Right(update.copy(newerVersions = Nel.of("2.0.1-M3".v)))
    assertEquals(localFilter(update, configWithAllowed), expected)
  }

  test("ignore update via config updates.ignore") {
    val update1 = ("org.http4s".g % "http4s-dsl".a % "0.17.0" %> "0.18.0").single
    val update2 = ("eu.timepit".g % "refined".a % "0.8.0" %> "0.8.1").single
    val config = RepoConfig(updates =
      UpdatesConfig(ignore = List(UpdatePattern(GroupId("eu.timepit"), Some("refined"), None)))
    )

    val initialState = MockState.empty
    val (state, filtered) =
      filterAlg.localFilterMany(config, List(update1, update2)).runSA(initialState).unsafeRunSync()

    assertEquals(filtered, List(update1))
    val expected = initialState.copy(
      trace = Vector(Log("Ignore eu.timepit:refined : 0.8.0 -> 0.8.1 (reason: ignored by config)"))
    )
    assertEquals(state, expected)
  }

  test("ignored versions are removed") {
    val update =
      ("org.scala-lang".g % "scala-compiler".a % "2.13.6" %> Nel.of("2.13.7", "2.13.8")).single
    val config = RepoConfig(updates =
      UpdatesConfig(ignore =
        List(
          UpdatePattern(
            GroupId("org.scala-lang"),
            Some("scala-compiler"),
            Some(VersionPattern(exact = Some("2.13.8")))
          )
        )
      )
    )
    val expected = Right(update.copy(newerVersions = Nel.of("2.13.7".v)))
    assertEquals(localFilter(update, config), expected)
  }

  test("ignore update via config updates.pin") {
    val update1 = ("org.http4s".g % "http4s-dsl".a % "0.17.0" %> "0.18.0").single
    val update2 = ("eu.timepit".g % "refined".a % "0.8.0" %> "0.8.1").single

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(update1.groupId, None, Some(VersionPattern(Some("0.17")))),
          UpdatePattern(
            update2.groupId,
            Some("refined"),
            Some(VersionPattern(Some("0.8")))
          )
        )
      )
    )

    val filtered = filterAlg
      .localFilterMany(config, List(update1, update2))
      .runA(MockState.empty)
      .unsafeRunSync()

    assertEquals(filtered, List(update2))
  }

  test("ignore update via config updates.allow") {
    val included = List(
      ("org.my1".g % "artifact".a % "0.8.0" %> "0.8.1").single,
      ("org.my2".g % "artifact".a % "0.8.0" %> "0.8.1").single,
      ("org.my2".g % "artifact".a % "0.8.0" %> "0.9.1").single
    )
    val notIncluded = List(
      ("org.http4s".g % "http4s-dsl".a % "0.17.0" %> "0.18.0").single,
      ("org.my1".g % "artifact".a % "0.8.0" %> "0.9.1").single,
      ("org.my3".g % "abc".a % "0.8.0" %> "0.8.1").single
    )

    val config = RepoConfig(
      updates = UpdatesConfig(
        allow = List(
          UpdatePattern(GroupId("org.my1"), None, Some(VersionPattern(Some("0.8")))),
          UpdatePattern(GroupId("org.my2"), None, None),
          UpdatePattern(GroupId("org.my3"), Some("artifact"), None)
        )
      )
    )

    val filtered = filterAlg
      .localFilterMany(config, included ++ notIncluded)
      .runA(MockState.empty)
      .unsafeRunSync()

    assertEquals(filtered, included)
  }

  test("ignore update via config updates.pin using suffix") {
    val update = ("com.microsoft.sqlserver".g % "mssql-jdbc".a % "7.2.2.jre8" %>
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")).single

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(VersionPattern(suffix = Some("jre8")))
          )
        )
      )
    )

    val filtered = localFilter(update, config)
    assertEquals(filtered, Right(update.copy(newerVersions = Nel.of("7.3.0.jre8".v))))
  }

  test("ignore update via config updates.ignore using suffix") {
    val update = ("com.microsoft.sqlserver".g % "mssql-jdbc".a % "7.2.2.jre8" %>
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")).single

    val config = RepoConfig(
      updates = UpdatesConfig(
        ignore = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(VersionPattern(suffix = Some("jre11")))
          )
        )
      )
    )

    val filtered = localFilter(update, config)
    assertEquals(filtered, Right(update.copy(newerVersions = Nel.of("7.3.0.jre8".v))))
  }

  test("ignore update via config updates.pin using prefix and suffix") {
    val update = ("com.microsoft.sqlserver".g % "mssql-jdbc".a % "7.2.2.jre8" %>
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")).single

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(VersionPattern(Some("7.2."), Some("jre8")))
          )
        )
      )
    )

    assertEquals(localFilter(update, config), Left(VersionPinnedByConfig(update)))
  }

  test("ignore version with 'contains' matcher") {
    val update =
      ("sqlserver".g % "mssql-jdbc".a % "7.2.2" %> Nel.of("7.3.0.feature.1", "7.3.0")).single
    val repoConfig = RepoConfigAlg.parseRepoConfig(
      """updates.ignore = [ { groupId = "sqlserver", version = { contains = "feature" } } ]"""
    )
    val obtained = repoConfig.flatMap(localFilter(update, _).leftMap(_.show))
    assertEquals(obtained, Right(update.copy(newerVersions = Nel.of("7.3.0".v))))
  }

  test("isDependencyConfigurationIgnored: false") {
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    assert(!isDependencyConfigurationIgnored(dependency.copy(configurations = Some("foo"))))
  }

  test("isDependencyConfigurationIgnored: true") {
    val dependency = "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    assert(isDependencyConfigurationIgnored(dependency.copy(configurations = Some("scalafmt"))))
  }
}
