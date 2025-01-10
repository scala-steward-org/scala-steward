package org.scalasteward.core.data

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOptionId
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.repoconfig.{PullRequestGroup, PullRequestUpdateFilter}

class GroupedUpdateTest extends FunSuite {
  val updateSingleSpecs2Core: Update.ForArtifactId =
    ("org.specs2".g % "specs2-core".a % "3.9.3" %> "3.9.5").single

  val updateSingleSpecs2Scalacheck: Update.ForArtifactId =
    ("org.specs2".g % "specs2-scalacheck".a % "3.9.3" %> "3.9.5").single

  val updateSingleTypelevelAlgebra: Update.ForArtifactId =
    ("org.typelevel".g % "algebra".a % "3.9.4" %> "3.9.5").single

  val updateSingleCirceCore: Update.ForArtifactId =
    ("circe".g % "core".a % "1.4.2" %> "1.5.0").single

  test(
    "GroupedUpdate.from: Do not group a Update.Single if the groupId does not match the filter"
  ) {
    val pullRequestGroupSpecs2: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.specs2".some).getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestGroupSpecs2),
        List(updateSingleTypelevelAlgebra)
      )
    assertEquals(grouped, List.empty)
    assertEquals(notGrouped, List(updateSingleTypelevelAlgebra))
  }

  test(
    "GroupedUpdate.from: Do not group multiple Update.Single if the groupId does not match the filter"
  ) {
    val pullRequestGroupTypeLevel: PullRequestGroup = PullRequestGroup(
      "typelevel",
      Some("update typelevel"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.typelevel".some).getOrElse(fail("Should not be called"))
      )
    )
    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestGroupTypeLevel),
        List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck)
      )
    assertEquals(grouped, List.empty)
    assertEquals(notGrouped, List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck))
  }

  test("GroupedUpdate.from: Group a Update.Single for a single GroupId filter") {
    val pullRequestGroupTypeLevel: PullRequestGroup = PullRequestGroup(
      "typelevel",
      Some("update typelevel"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.typelevel".some).getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestGroupTypeLevel),
        List(updateSingleTypelevelAlgebra)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped("typelevel", Some("update typelevel"), List(updateSingleTypelevelAlgebra))
      )
    )
    assertEquals(notGrouped, List.empty)
  }

  test("GroupedUpdate.from: Group multiple Update.Single for a single GroupId filter") {
    val pullRequestGroupSpecs2: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.specs2".some).getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestGroupSpecs2),
        List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2",
          Some("update specs2"),
          List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck)
        )
      )
    )
    assertEquals(notGrouped, List.empty)
  }

  test("GroupedUpdate.from: Group multiple Updates for multiple GroupId filters") {
    val pullRequestGroupSpecs2: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.specs2".some).getOrElse(fail("Should not be called"))
      )
    )

    val pullRequestGroupTypeLevel: PullRequestGroup = PullRequestGroup(
      "typelevel",
      Some("update typelevel"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.typelevel".some).getOrElse(fail("Should not be called"))
      )
    )

    val updates = List(
      updateSingleSpecs2Core,
      updateSingleSpecs2Scalacheck,
      updateSingleTypelevelAlgebra,
      updateSingleCirceCore
    )
    val pullRequestGroups = List(pullRequestGroupSpecs2, pullRequestGroupTypeLevel)

    val (grouped, notGrouped) = Update.groupByPullRequestGroup(pullRequestGroups, updates)

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2",
          Some("update specs2"),
          List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck)
        ),
        Update.Grouped("typelevel", Some("update typelevel"), List(updateSingleTypelevelAlgebra))
      )
    )
    assertEquals(notGrouped, List(updateSingleCirceCore))
  }

  val pullRequestGroupOrgWildcard: PullRequestGroup = PullRequestGroup(
    "org",
    Some("update org"),
    NonEmptyList.one(PullRequestUpdateFilter("org.*".some).getOrElse(fail("Should not be called")))
  )

  test("GroupedUpdate.from: Group multiple Updates for a GroupId filter with a wildcard") {
    val updates = List(
      updateSingleSpecs2Core,
      updateSingleSpecs2Scalacheck,
      updateSingleTypelevelAlgebra,
      updateSingleCirceCore
    )
    val pullRequestGroups = List(pullRequestGroupOrgWildcard)

    val (grouped, notGrouped) = Update.groupByPullRequestGroup(pullRequestGroups, updates)

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "org",
          Some("update org"),
          List(updateSingleSpecs2Core, updateSingleSpecs2Scalacheck, updateSingleTypelevelAlgebra)
        )
      )
    )
    assertEquals(notGrouped, List(updateSingleCirceCore))
  }

  test("GroupedUpdate.from: Group everything when using * for OrganisationId") {
    val pullRequestGroupWildcardAll: PullRequestGroup = PullRequestGroup(
      "all wildcard",
      Some("update all wildcard"),
      NonEmptyList.one(PullRequestUpdateFilter("*".some).getOrElse(fail("Should not be called")))
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestGroupWildcardAll),
        List(
          updateSingleTypelevelAlgebra,
          updateSingleSpecs2Core,
          updateSingleSpecs2Scalacheck
        )
      )
    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "all wildcard",
          Some("update all wildcard"),
          List(
            updateSingleTypelevelAlgebra,
            updateSingleSpecs2Core,
            updateSingleSpecs2Scalacheck
          )
        )
      )
    )
    assertEquals(notGrouped, List.empty)
  }

  test("GroupedUpdate.from: Group Update.Group for an ArtifactId filter without a group") {
    val pullRequestArtifactSpecs2CoreWithoutGroup: PullRequestGroup = PullRequestGroup(
      "specs2 core",
      Some("update specs2 core"),
      NonEmptyList.one(
        PullRequestUpdateFilter(None, "specs2-core".some).getOrElse(fail("Should not be called"))
      )
    )
    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2CoreWithoutGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core)
      )
    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2 core",
          Some("update specs2 core"),
          List(updateSingleSpecs2Core)
        )
      )
    )
    assertEquals(notGrouped, List(updateSingleSpecs2Scalacheck))
  }

  val pullRequestArtifactSpecs2CoreWithGroup: PullRequestGroup = PullRequestGroup(
    "specs2 core",
    Some("update specs2 core"),
    NonEmptyList.one(
      PullRequestUpdateFilter("org.specs2".some, "specs2-core".some).getOrElse(
        fail("Should not be called")
      )
    )
  )

  test("GroupedUpdate.from: Group multiple Update.Single for an ArtifactId filter with a group") {
    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2CoreWithGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core)
      )
    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2 core",
          Some("update specs2 core"),
          List(updateSingleSpecs2Core)
        )
      )
    )
    assertEquals(notGrouped, List(updateSingleSpecs2Scalacheck))
  }

  test(
    "GroupedUpdate.from: Not group multiple Update.Single for an ArtifactId filter with an invalid group"
  ) {
    val pullRequestArtifactSpecs2CoreWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "specs2 core",
      Some("update specs2 core"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.invalid".some, "specs2-core".some).getOrElse(
          fail("Should not be called")
        )
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2CoreWithInvalidGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core)
      )

    assertEquals(grouped, List.empty)
    assertEquals(notGrouped, List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core))
  }

  test(
    "GroupedUpdate.from: Group multiple Update.Single for an ArtifactId filter with a wildcard"
  ) {
    val pullRequestArtifactSpecs2WildcardWithoutGroup: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter(None, "specs2-*".some).getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithoutGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core, updateSingleTypelevelAlgebra)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2",
          Some("update specs2"),
          List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core)
        )
      )
    )
    assertEquals(notGrouped, List(updateSingleTypelevelAlgebra))
  }

  test(
    "GroupedUpdate.from: Group multiple Update.Single for an ArtifactId filter with a wildcard and a group"
  ) {
    val pullRequestArtifactSpecs2WildcardWithGroup: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.specs2".some, "specs2-*".some).getOrElse(
          fail("Should not be called")
        )
      )
    )
    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core, updateSingleTypelevelAlgebra)
      )
    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "specs2",
          Some("update specs2"),
          List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core)
        )
      )
    )
    assertEquals(notGrouped, List(updateSingleTypelevelAlgebra))
  }

  test(
    "GroupedUpdate.from: Not group multiple Update.Single for an ArtifactId filter with a wildcard an invalid group"
  ) {
    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "specs2",
      Some("update specs2"),
      NonEmptyList.one(
        PullRequestUpdateFilter("org.invalid".some, "specs2-*".some).getOrElse(
          fail("Should not be called")
        )
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core, updateSingleTypelevelAlgebra)
      )

    assertEquals(grouped, List.empty)
    assertEquals(
      notGrouped,
      List(updateSingleSpecs2Scalacheck, updateSingleSpecs2Core, updateSingleTypelevelAlgebra)
    )
  }

  val majorUpdate: Update.ForArtifactId = ("org.major".g % "major".a % "3.1.2" %> "4.0.0").single
  val minorUpdate: Update.ForArtifactId = ("org.minor".g % "minor".a % "3.1.2" %> "3.2.0").single
  val patchUpdate: Update.ForArtifactId = ("org.patch".g % "patch".a % "3.1.2" %> "3.1.3").single

  test(
    "GroupedUpdate.from: Group major updates"
  ) {
    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "major",
      Some("update major"),
      NonEmptyList.one(
        PullRequestUpdateFilter(version = SemVer.Change.Major.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped("major", Some("update major"), List(majorUpdate))
      )
    )

    assertEquals(notGrouped, List(minorUpdate, patchUpdate))
  }

  test(
    "GroupedUpdate.from: Group minor updates"
  ) {
    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "minor",
      Some("update minor"),
      NonEmptyList.one(
        PullRequestUpdateFilter(version = SemVer.Change.Minor.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped("minor", Some("update minor"), List(minorUpdate))
      )
    )

    assertEquals(notGrouped, List(majorUpdate, patchUpdate))
  }

  test(
    "GroupedUpdate.from: Group patch updates"
  ) {
    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "patch",
      Some("update patch"),
      NonEmptyList.one(
        PullRequestUpdateFilter(version = SemVer.Change.Patch.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped("patch", Some("update patch"), List(patchUpdate))
      )
    )

    assertEquals(notGrouped, List(majorUpdate, minorUpdate))
  }

  test(
    "GroupedUpdate.from: Group minor & patch updates"
  ) {
    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "patch & minor",
      Some("update patch & minor"),
      NonEmptyList.of(
        PullRequestUpdateFilter(version = SemVer.Change.Patch.some)
          .getOrElse(fail("Should not be called")),
        PullRequestUpdateFilter(version = SemVer.Change.Minor.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "patch & minor",
          Some("update patch & minor"),
          List(minorUpdate, patchUpdate)
        )
      )
    )

    assertEquals(notGrouped, List(majorUpdate))
  }

  test(
    "GroupedUpdate.from: Group major & minor updates with version numbers containing a date and commit hash"
  ) {
    val majorUpdate: Update.ForArtifactId =
      ("org.major".g % "major".a % "1.0.0-20220920-180024-dd318047" %> "2.0.0-20220922-180024-dd318047").single
    val minorUpdate: Update.ForArtifactId =
      ("org.minor".g % "minor".a % "1.0.0-20220920-180024-dd318047" %> "1.1.0-20220922-180024-dd318047").single
    val patchUpdate: Update.ForArtifactId =
      ("org.patch".g % "patch".a % "1.0.0-20220920-180024-dd318047" %> "1.0.1-20220922-180024-dd318047").single

    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "major & minor",
      Some("update major & minor"),
      NonEmptyList.of(
        PullRequestUpdateFilter(version = SemVer.Change.Major.some)
          .getOrElse(fail("Should not be called")),
        PullRequestUpdateFilter(version = SemVer.Change.Minor.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped(
          "major & minor",
          Some("update major & minor"),
          List(majorUpdate, minorUpdate)
        )
      )
    )

    assertEquals(notGrouped, List(patchUpdate))
  }

  test(
    "GroupedUpdate.from: Group patch updates with version numbers containing a date and commit hash"
  ) {
    val majorUpdate: Update.ForArtifactId =
      ("org.major".g % "major".a % "1.0.0-20220920-180024-dd318047" %> "2.0.0-20220922-180024-dd318047").single
    val minorUpdate: Update.ForArtifactId =
      ("org.minor".g % "minor".a % "1.0.0-20220920-180024-dd318047" %> "1.1.0-20220922-180024-dd318047").single
    val patchUpdate: Update.ForArtifactId =
      ("org.patch".g % "patch".a % "1.0.0-20220920-180024-dd318047" %> "1.0.1-20220920-180024-dd318047").single

    val pullRequestArtifactSpecs2WildcardWithInvalidGroup: PullRequestGroup = PullRequestGroup(
      "patch",
      Some("update patch"),
      NonEmptyList.of(
        PullRequestUpdateFilter(version = SemVer.Change.Patch.some)
          .getOrElse(fail("Should not be called"))
      )
    )

    val (grouped, notGrouped) =
      Update.groupByPullRequestGroup(
        List(pullRequestArtifactSpecs2WildcardWithInvalidGroup),
        List(majorUpdate, minorUpdate, patchUpdate)
      )

    assertEquals(
      grouped,
      List(
        Update.Grouped("patch", Some("update patch"), List(patchUpdate))
      )
    )

    assertEquals(notGrouped, List(majorUpdate, minorUpdate))
  }
}
