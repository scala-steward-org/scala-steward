package org.scalasteward.core.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}
import org.scalasteward.core.mock.MockContext.context.filterAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.{RepoConfig, UpdatePattern, UpdatesConfig}
import org.scalasteward.core.update.FilterAlg._
import org.scalasteward.core.util.Nel

class FilterAlgTest extends FunSuite {
  private val config = RepoConfig.empty

  test("localFilter: SNAP -> SNAP") {
    val update = Single("org.scalatest" % "scalatest" % "3.0.8-SNAP2", Nel.of("3.0.8-SNAP10"))
    assertEquals(localFilter(update, config), Right(update))
  }

  test("localFilter: RC -> SNAP") {
    val update = Single("org.scalatest" % "scalatest" % "3.0.8-RC2", Nel.of("3.1.0-SNAP10"))
    assertEquals(localFilter(update, config), Left(NoSuitableNextVersion(update)))
  }

  test("localFilter: update without bad version") {
    val update = Single("com.jsuereth" % "sbt-pgp" % "1.1.0", Nel.of("1.1.2", "2.0.0"))
    assertEquals(localFilter(update, config), Right(update.copy(newerVersions = Nel.of("1.1.2"))))
  }

  test("localFilter: update with bad version") {
    val update = Single("com.jsuereth" % "sbt-pgp" % "1.1.2-1", Nel.of("1.1.2", "2.0.0"))
    assertEquals(localFilter(update, config), Right(update.copy(newerVersions = Nel.of("2.0.0"))))
  }

  test("localFilter: update with bad version 2") {
    val update = Single(
      "net.sourceforge.plantuml" % "plantuml" % "1.2019.11",
      Nel.of("7726", "8020", "2017.09", "1.2019.12")
    )
    assertEquals(
      localFilter(update, config),
      Right(update.copy(newerVersions = Nel.of("1.2019.12")))
    )
  }

  test("localFilter: update with only bad versions") {
    val update = Single("org.http4s" % "http4s-dsl" % "0.18.0", Nel.of("0.19.0"))
    assertEquals(localFilter(update, config), Left(BadVersions(update)))
  }

  test("localFilter: update to pre-release of a different series") {
    val update = Single("com.jsuereth" % "sbt-pgp" % "1.1.2-1", Nel.of("2.0.1-M3"))
    assertEquals(localFilter(update, config), Left(NoSuitableNextVersion(update)))
  }

  test("ignore update via config updates.ignore") {
    val update1 = Single("org.http4s" % "http4s-dsl" % "0.17.0", Nel.of("0.18.0"))
    val update2 = Single("eu.timepit" % "refined" % "0.8.0", Nel.of("0.8.1"))
    val config = RepoConfig(updates =
      UpdatesConfig(ignore = List(UpdatePattern(GroupId("eu.timepit"), Some("refined"), None)))
    )

    val initialState = MockState.empty
    val (state, filtered) =
      filterAlg.localFilterMany(config, List(update1, update2)).run(initialState).unsafeRunSync()

    assertEquals(filtered, List(update1))
    val expected = initialState.copy(
      logs = Vector(
        (None, "Ignore eu.timepit:refined : 0.8.0 -> 0.8.1 (reason: ignored by config)")
      )
    )
    assertEquals(state, expected)
  }

  test("ignore update via config updates.pin") {
    val update1 = Single("org.http4s" % "http4s-dsl" % "0.17.0", Nel.of("0.18.0"))
    val update2 = Single("eu.timepit" % "refined" % "0.8.0", Nel.of("0.8.1"))

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(update1.groupId, None, Some(UpdatePattern.Version(Some("0.17"), None))),
          UpdatePattern(
            update2.groupId,
            Some("refined"),
            Some(UpdatePattern.Version(Some("0.8"), None))
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
      Single("org.my1" % "artifact" % "0.8.0", Nel.of("0.8.1")),
      Single("org.my2" % "artifact" % "0.8.0", Nel.of("0.8.1")),
      Single("org.my2" % "artifact" % "0.8.0", Nel.of("0.9.1"))
    )
    val notIncluded = List(
      Single("org.http4s" % "http4s-dsl" % "0.17.0", Nel.of("0.18.0")),
      Single("org.my1" % "artifact" % "0.8.0", Nel.of("0.9.1")),
      Single("org.my3" % "abc" % "0.8.0", Nel.of("0.8.1"))
    )

    val config = RepoConfig(
      updates = UpdatesConfig(
        allow = List(
          UpdatePattern(GroupId("org.my1"), None, Some(UpdatePattern.Version(Some("0.8"), None))),
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
    val update = Single(
      "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.2.jre8",
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")
    )

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(UpdatePattern.Version(None, Some("jre8")))
          )
        )
      )
    )

    val filtered = localFilter(update, config)
    assertEquals(filtered, Right(update.copy(newerVersions = Nel.of("7.3.0.jre8"))))
  }

  test("ignore update via config updates.ignore using suffix") {
    val update = Single(
      "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.2.jre8",
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")
    )

    val config = RepoConfig(
      updates = UpdatesConfig(
        ignore = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(UpdatePattern.Version(None, Some("jre11")))
          )
        )
      )
    )

    val filtered = localFilter(update, config)
    assertEquals(filtered, Right(update.copy(newerVersions = Nel.of("7.3.0.jre8"))))
  }

  test("ignore update via config updates.pin using prefix and suffix") {
    val update = Single(
      "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.2.jre8",
      Nel.of("7.2.2.jre11", "7.3.0.jre8", "7.3.0.jre11")
    )

    val config = RepoConfig(
      updates = UpdatesConfig(
        pin = List(
          UpdatePattern(
            update.groupId,
            Some(update.artifactId.name),
            Some(UpdatePattern.Version(Some("7.2."), Some("jre8")))
          )
        )
      )
    )

    assertEquals(localFilter(update, config), Left(VersionPinnedByConfig(update)))
  }

  test("isScalaDependency: true") {
    val dependency = Dependency(
      GroupId("org.scala-lang"),
      ArtifactId("scala-compiler", "scala-compiler_2.12"),
      "2.12.10"
    )
    assert(isScalaDependency(dependency))
  }

  test("isScalaDependency: false") {
    val dependency =
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    assert(!isScalaDependency(dependency))
  }

  test("isScalaDependencyIgnored: true") {
    val dependency = Dependency(
      GroupId("org.scala-lang"),
      ArtifactId("scala-compiler", "scala-compiler_2.12"),
      "2.12.10"
    )
    assert(isScalaDependencyIgnored(dependency, ignoreScalaDependency = true))
  }

  test("isScalaDependencyIgnored: false") {
    val dependency = Dependency(
      GroupId("org.scala-lang"),
      ArtifactId("scala-compiler", "scala-compiler_2.12"),
      "2.12.10"
    )
    assert(!isScalaDependencyIgnored(dependency, ignoreScalaDependency = false))
  }

  test("isDependencyConfigurationIgnored: false") {
    val dependency =
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    assert(!isDependencyConfigurationIgnored(dependency.copy(configurations = Some("foo"))))
  }

  test("isDependencyConfigurationIgnored: true") {
    val dependency =
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    assert(
      isDependencyConfigurationIgnored(
        dependency.copy(configurations = Some("scalafmt"))
      )
    )
  }
}
