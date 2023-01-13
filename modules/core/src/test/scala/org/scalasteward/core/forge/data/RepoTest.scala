package org.scalasteward.core.forge.data

import munit.FunSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch

class RepoTest extends FunSuite {
  test("parse") {
    assertEquals(Repo.parse("- typelevel/cats-effect"), Some(Repo("typelevel", "cats-effect")))
    assertEquals(Repo.parse("- group1/group2/project1"), Some(Repo("group1/group2", "project1")))
    assertEquals(
      Repo.parse("- typelevel/cats-effect:3.x"),
      Some(Repo("typelevel", "cats-effect", Some(Branch("3.x"))))
    )
    assertEquals(
      Repo.parse("- typelevel/cats-effect:series/3.x"),
      Some(Repo("typelevel", "cats-effect", Some(Branch("series/3.x"))))
    )

    assertEquals(Repo.parse("typelevel/cats-effect"), None)
    assertEquals(Repo.parse("- typelevel-cats-effect"), None)
  }
}
