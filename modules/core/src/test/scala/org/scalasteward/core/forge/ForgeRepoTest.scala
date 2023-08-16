package org.scalasteward.core.forge

import munit.FunSuite
import org.http4s.Uri
import org.http4s.implicits._
import org.scalasteward.core.forge.ForgeType._

/** As much as possible, uris in this test suite should aim to be real, clickable, uris that
  * actually go to real pages, allowing developers working against this test suite to verify that
  * the url patterns constructed here actually _are_ valid, and match what forges are currently
  * using in the real world.
  */
class ForgeRepoTest extends FunSuite {

  def check(
      repo: ForgeRepo,
      expectedReadme: Uri,
      diffTags: (String, String),
      expectedDiff: Uri
  ): Unit = {
    assertEquals(repo.fileUrlFor("README.md"), expectedReadme)
    assertEquals(repo.diffUrlFor(diffTags._1, diffTags._2), expectedDiff)
  }

  test("GitHub url patterns") {
    check(
      ForgeRepo(GitHub, uri"https://github.com/scala-steward-org/scala-steward-action"),
      uri"https://github.com/scala-steward-org/scala-steward-action/blob/master/README.md",
      "v2.55.0" -> "v2.56.0",
      uri"https://github.com/scala-steward-org/scala-steward-action/compare/v2.55.0...v2.56.0"
    )
  }

  test("GitLab url patterns") {
    check(
      ForgeRepo(GitLab, uri"https://gitlab.com/gitlab-org/gitlab"),
      uri"https://gitlab.com/gitlab-org/gitlab/blob/master/README.md",
      "v15.11.8-ee" -> "v15.11.9-ee",
      uri"https://gitlab.com/gitlab-org/gitlab/compare/v15.11.8-ee...v15.11.9-ee"
    )
  }

  test("Gitea url patterns") {
    check(
      ForgeRepo(Gitea, uri"https://gitea.com/lunny/levelqueue"),
      uri"https://gitea.com/lunny/levelqueue/src/branch/master/README.md",
      "v0.1.0" -> "v0.2.0",
      uri"https://gitea.com/lunny/levelqueue/compare/v0.1.0...v0.2.0"
    )
  }

  test("Azure url patterns") {
    check(
      ForgeRepo(
        AzureRepos,
        uri"https://dev.azure.com/rtyley/scala-steward-testing/_git/scala-steward-testing"
      ),
      uri"https://dev.azure.com/rtyley/scala-steward-testing/_git/scala-steward-testing?path=README.md",
      "v1.0.0" -> "v1.0.1",
      uri"https://dev.azure.com/rtyley/scala-steward-testing/_git/scala-steward-testing/branchCompare?baseVersion=GTv1.0.0&targetVersion=GTv1.0.1"
    )
  }

  test("BitBucket url patterns") {
    check(
      ForgeRepo(Bitbucket, uri"https://bitbucket.org/rtyley/scala-steward-test-repo"),
      uri"https://bitbucket.org/rtyley/scala-steward-test-repo/src/master/README.md",
      "v1.0.0" -> "v1.0.1",
      uri"https://bitbucket.org/rtyley/scala-steward-test-repo/compare/v1.0.1..v1.0.0#diff"
    )
  }

  test("BitBucket Server url patterns") {
    check(
      ForgeRepo(BitbucketServer, uri"https://bitbucket-server.on-prem.com/foo/bar"),
      uri"https://bitbucket-server.on-prem.com/foo/bar/browse/README.md",
      "v1.0.0" -> "v1.0.1",
      uri"https://bitbucket-server.on-prem.com/foo/bar/compare/v1.0.1..v1.0.0#diff"
    )
  }
}
