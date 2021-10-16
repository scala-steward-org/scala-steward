package org.scalasteward.core.vcs.data

import munit.FunSuite
import org.scalasteward.core.vcs.data.Repo.repoKeyDecoder
import org.scalasteward.core.git.Branch

class RepoTest extends FunSuite {
  test("decode") {
    assertEquals(repoKeyDecoder("owner/repo"), Some(Repo("owner", "repo")))
    assertEquals(
      repoKeyDecoder("owner/repo#branch"),
      Some(Repo("owner", "repo", Some(Branch("branch"))))
    )
  }

  test("decode sub group") {
    assertEquals(repoKeyDecoder("group1/group2/project1"), Some(Repo("group1/group2", "project1")))
    assertEquals(
      repoKeyDecoder("group1/group2/project1#branch"),
      Some(Repo("group1/group2", "project1", Some(Branch("branch"))))
    )
  }
}
