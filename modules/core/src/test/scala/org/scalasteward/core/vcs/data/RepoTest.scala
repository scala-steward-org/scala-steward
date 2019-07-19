package org.scalasteward.core.vcs.data

import org.scalatest.{FunSuite, Matchers}

class RepoTest extends FunSuite with Matchers {
  import Repo.repoKeyDecoder

  test("decode") {
    repoKeyDecoder("owner/repo") shouldBe Some(Repo("owner", "repo"))
  }

  test("decode sub group") {
    repoKeyDecoder("group1/group2/project1") shouldBe Some(Repo("group1/group2", "project1"))
  }
}
