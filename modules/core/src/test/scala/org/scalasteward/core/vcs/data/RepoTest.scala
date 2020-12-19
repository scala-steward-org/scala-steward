package org.scalasteward.core.vcs.data

import munit.FunSuite
import org.scalasteward.core.vcs.data.Repo.repoKeyDecoder

class RepoTest extends FunSuite {
  test("decode") {
    assertEquals(repoKeyDecoder("owner/repo"), Some(Repo("owner", "repo")))
  }

  test("decode sub group") {
    assertEquals(repoKeyDecoder("group1/group2/project1"), Some(Repo("group1/group2", "project1")))
  }
}
