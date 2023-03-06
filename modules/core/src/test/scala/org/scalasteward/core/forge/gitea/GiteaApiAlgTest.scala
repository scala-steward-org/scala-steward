package org.scalasteward.core.forge.gitea

import cats.syntax.semigroupk._
import io.circe.literal._
import munit.CatsEffectSuite
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{BasicCredentials, HttpApp}
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.GiteaCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.{ForgeSelection, ForgeType}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}

class GiteaApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val user = AuthenticatedUser("user", "pass")
  private val repo = Repo("foo", "baz")

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))
  private val auth = HttpApp[MockEff] { request =>
    (request: @unchecked) match {
      case _ if !request.headers.get[Authorization].contains(basicAuth) => Forbidden()
    }
  }

  object PageQ extends QueryParamDecoderMatcher[Int]("page")

  private val httpApp = HttpApp[MockEff] {
    case GET -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo =>
      Ok(getRepo)
    case GET -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "branches" / "main" =>
      Ok(getBranch)
    case GET -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "pulls" :? PageQ(page) =>
      if (page == 1) Ok(listPulls) else Ok(json"[]")
    case PATCH -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "pulls" / "1" =>
      Ok(closePull1)
    case POST -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "issues" / "1" / "comments" =>
      Created(commentPR1)
    case POST -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "pulls" =>
      Created(createPR2)
    case GET -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "labels" :? PageQ(page) =>
      if (page == 1) Ok(listLabels) else Ok(json"[]")
    case POST -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "labels" =>
      Created(createLabel)
    case POST -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "issues" / "2" / "labels" =>
      Ok(listLabels)
    case POST -> Root / "api" / "v1" / "repos" / repo.owner / repo.repo / "forks" =>
      Ok(forkRepo)
    case _ => NotFound()
  }

  private val state = MockState.empty.copy(clientResponses = auth <+> httpApp)

  private val forgeCfg = config.forgeCfg.copy(
    tpe = ForgeType.Gitea,
    apiHost = config.forgeCfg.apiHost / "api" / "v1"
  )
  private val giteaAlg = ForgeSelection.forgeApiAlg[MockEff](forgeCfg, GiteaCfg(), user)

  test("getRepo") {
    giteaAlg
      .getRepo(repo)
      .runA(state)
      .map { repoOut =>
        assertEquals(
          repoOut,
          RepoOut(
            name = "baz",
            owner = UserOut("foo"),
            parent = None,
            clone_url = uri"https://git.example.com/foo/baz.git",
            default_branch = Branch("main")
          )
        )
      }
  }

  test("getBranch") {
    val branch = Branch("main")
    val sha1 = Sha1.unsafeFrom("6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3")
    giteaAlg
      .getBranch(repo, branch)
      .runA(state)
      .map { branchOut =>
        assertEquals(branchOut, BranchOut(name = Branch("main"), commit = CommitOut(sha1)))
      }
  }

  test("listPulls") {
    giteaAlg
      .listPullRequests(repo, "qux", Branch("main"))
      .runA(state)
      .map { pulls =>
        assertEquals(
          pulls,
          List(
            PullRequestOut(
              html_url = uri"https://git.example.com/foo/baz/pulls/1",
              state = PullRequestState.Open,
              number = PullRequestNumber(1),
              title = "update README"
            )
          )
        )
      }
  }

  test("closePullRequest") {
    giteaAlg
      .closePullRequest(repo, PullRequestNumber(1))
      .runA(state)
      .map { pull =>
        val out = PullRequestOut(
          html_url = uri"https://git.example.com/foo/baz/pulls/1",
          state = PullRequestState.Closed,
          number = PullRequestNumber(1),
          title = "update README"
        )
        assertEquals(pull, out)
      }
  }

  test("comment pull request") {
    giteaAlg
      .commentPullRequest(repo, PullRequestNumber(1), "hi")
      .runA(state)
      .map { comment =>
        assertEquals(comment, Comment("hi"))
      }
  }

  test("create pull request") {
    giteaAlg
      .createPullRequest(
        repo,
        NewPullRequestData(
          title = "pr test",
          body = "hi",
          head = "pr2",
          base = Branch("main"),
          labels = Nil,
          assignees = Nil,
          reviewers = Nil
        )
      )
      .runA(state)
      .map { prOut =>
        assertEquals(
          prOut,
          PullRequestOut(
            html_url = uri"https://git.example.com/foo/baz/pulls/2",
            state = PullRequestState.Open,
            number = PullRequestNumber(2),
            title = "pr test"
          )
        )
      }
  }

  test("list labels") {
    giteaAlg
      .asInstanceOf[GiteaApiAlg[MockEff]]
      .listLabels(repo)
      .runA(state)
      .map { labels =>
        assertEquals(
          labels,
          Vector(
            GiteaApiAlg.Label(2, "label1")
          )
        )
      }
  }

  test("create label") {
    giteaAlg
      .asInstanceOf[GiteaApiAlg[MockEff]]
      .createLabel(repo, "label1")
      .runA(state)
      .map { label =>
        assertEquals(
          label,
          GiteaApiAlg.Label(2, "label1")
        )
      }
  }

  test("create fork") {
    giteaAlg
      .createFork(repo)
      .runA(state)
      .map { repoOut =>
        val parent = RepoOut(
          name = "baz",
          owner = UserOut("foo"),
          parent = None,
          clone_url = uri"https://git.example.com/foo/baz.git",
          default_branch = Branch("main")
        )
        assertEquals(
          repoOut,
          RepoOut(
            name = "baz",
            owner = UserOut("bar"),
            parent = Some(parent),
            clone_url = uri"https://git.example.com/bar/baz.git",
            default_branch = Branch("main")
          )
        )
      }
  }

  def getRepo =
    json""" {
            "id": 5,
            "owner": {
              "id": 2,
              "login": "foo",
              "login_name": "",
              "full_name": "",
              "email": "foo@example.com",
              "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
              "language": "",
              "is_admin": false,
              "last_login": "0001-01-01T00:00:00Z",
              "created": "2023-01-25T21:48:28+09:00",
              "restricted": false,
              "active": false,
              "prohibit_login": false,
              "location": "",
              "website": "",
              "description": "",
              "visibility": "private",
              "followers_count": 0,
              "following_count": 0,
              "starred_repos_count": 0,
              "username": "foo"
            },
            "name": "baz",
            "full_name": "foo/baz",
            "description": "",
            "empty": false,
            "private": false,
            "fork": false,
            "template": false,
            "parent": null,
            "mirror": false,
            "size": 93,
            "language": "",
            "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
            "html_url": "https://git.example.com/foo/baz",
            "ssh_url": "forgejo@git.example.com:foo/baz.git",
            "clone_url": "https://git.example.com/foo/baz.git",
            "original_url": "",
            "website": "",
            "stars_count": 0,
            "forks_count": 0,
            "watchers_count": 1,
            "open_issues_count": 0,
            "open_pr_counter": 0,
            "release_counter": 0,
            "default_branch": "main",
            "archived": false,
            "created_at": "2023-02-07T16:52:32+09:00",
            "updated_at": "2023-02-07T16:54:32+09:00",
            "permissions": {
              "admin": true,
              "push": true,
              "pull": true
            },
            "has_issues": true,
            "internal_tracker": {
              "enable_time_tracker": true,
              "allow_only_contributors_to_track_time": true,
              "enable_issue_dependencies": true
            },
            "has_wiki": true,
            "has_pull_requests": true,
            "has_projects": true,
            "ignore_whitespace_conflicts": false,
            "allow_merge_commits": true,
            "allow_rebase": true,
            "allow_rebase_explicit": true,
            "allow_squash_merge": true,
            "allow_rebase_update": true,
            "default_delete_branch_after_merge": false,
            "default_merge_style": "merge",
            "avatar_url": "",
            "internal": true,
            "mirror_interval": "",
            "mirror_updated": "0001-01-01T00:00:00Z",
            "repo_transfer": null
          } """

  def getBranch =
    json""" {
            "name": "main",
            "commit": {
              "id": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
              "message": "add readme\n",
              "url": "https://git.example.com/foo/baz/commit/6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
              "author": {
                "name": "foo",
                "email": "foo@example.com",
                "username": ""
              },
              "committer": {
                "name": "foo",
                "email": "foo@example.com",
                "username": ""
              },
              "verification": {
                "verified": false,
                "reason": "gpg.error.not_signed_commit",
                "signature": "",
                "signer": null,
                "payload": ""
              },
              "timestamp": "2023-02-07T16:54:11+09:00",
              "added": null,
              "removed": null,
              "modified": null
            },
            "protected": false,
            "required_approvals": 0,
            "enable_status_check": false,
            "status_check_contexts": [],
            "user_can_push": true,
            "user_can_merge": true,
            "effective_branch_protection_name": ""
          } """

  def listPulls =
    json""" [
            {
              "id": 1,
              "url": "https://git.example.com/foo/baz/pulls/1",
              "number": 1,
              "user": {
                "id": 2,
                "login": "foo",
                "login_name": "",
                "full_name": "",
                "email": "foo@example.com",
                "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
                "language": "",
                "is_admin": false,
                "last_login": "0001-01-01T00:00:00Z",
                "created": "2023-01-25T21:48:28+09:00",
                "restricted": false,
                "active": false,
                "prohibit_login": false,
                "location": "",
                "website": "",
                "description": "",
                "visibility": "private",
                "followers_count": 0,
                "following_count": 0,
                "starred_repos_count": 0,
                "username": "foo"
              },
              "title": "update README",
              "body": "",
              "labels": [],
              "milestone": null,
              "assignee": null,
              "assignees": null,
              "state": "open",
              "is_locked": false,
              "comments": 0,
              "html_url": "https://git.example.com/foo/baz/pulls/1",
              "diff_url": "https://git.example.com/foo/baz/pulls/1.diff",
              "patch_url": "https://git.example.com/foo/baz/pulls/1.patch",
              "mergeable": true,
              "merged": false,
              "merged_at": null,
              "merge_commit_sha": null,
              "merged_by": null,
              "allow_maintainer_edit": false,
              "base": {
                "label": "main",
                "ref": "main",
                "sha": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
                "repo_id": 5,
                "repo": {
                  "id": 5,
                  "owner": {
                    "id": 2,
                    "login": "foo",
                    "login_name": "",
                    "full_name": "",
                    "email": "foo@example.com",
                    "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
                    "language": "",
                    "is_admin": false,
                    "last_login": "0001-01-01T00:00:00Z",
                    "created": "2023-01-25T21:48:28+09:00",
                    "restricted": false,
                    "active": false,
                    "prohibit_login": false,
                    "location": "",
                    "website": "",
                    "description": "",
                    "visibility": "private",
                    "followers_count": 0,
                    "following_count": 0,
                    "starred_repos_count": 0,
                    "username": "foo"
                  },
                  "name": "baz",
                  "full_name": "foo/baz",
                  "description": "",
                  "empty": false,
                  "private": false,
                  "fork": false,
                  "template": false,
                  "parent": null,
                  "mirror": false,
                  "size": 105,
                  "language": "",
                  "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
                  "html_url": "https://git.example.com/foo/baz",
                  "ssh_url": "forgejo@git.example.com:foo/baz.git",
                  "clone_url": "https://git.example.com/foo/baz.git",
                  "original_url": "",
                  "website": "",
                  "stars_count": 0,
                  "forks_count": 0,
                  "watchers_count": 1,
                  "open_issues_count": 0,
                  "open_pr_counter": 1,
                  "release_counter": 0,
                  "default_branch": "main",
                  "archived": false,
                  "created_at": "2023-02-07T16:52:32+09:00",
                  "updated_at": "2023-02-07T16:58:43+09:00",
                  "permissions": {
                    "admin": true,
                    "push": true,
                    "pull": true
                  },
                  "has_issues": true,
                  "internal_tracker": {
                    "enable_time_tracker": true,
                    "allow_only_contributors_to_track_time": true,
                    "enable_issue_dependencies": true
                  },
                  "has_wiki": true,
                  "has_pull_requests": true,
                  "has_projects": true,
                  "ignore_whitespace_conflicts": false,
                  "allow_merge_commits": true,
                  "allow_rebase": true,
                  "allow_rebase_explicit": true,
                  "allow_squash_merge": true,
                  "allow_rebase_update": true,
                  "default_delete_branch_after_merge": false,
                  "default_merge_style": "merge",
                  "avatar_url": "",
                  "internal": true,
                  "mirror_interval": "",
                  "mirror_updated": "0001-01-01T00:00:00Z",
                  "repo_transfer": null
                }
              },
              "head": {
                "label": "qux",
                "ref": "qux",
                "sha": "291355f22ea6e0179f85ee5ace4af67ab61e0adb",
                "repo_id": 5,
                "repo": {
                  "id": 5,
                  "owner": {
                    "id": 2,
                    "login": "foo",
                    "login_name": "",
                    "full_name": "",
                    "email": "foo@example.com",
                    "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
                    "language": "",
                    "is_admin": false,
                    "last_login": "0001-01-01T00:00:00Z",
                    "created": "2023-01-25T21:48:28+09:00",
                    "restricted": false,
                    "active": false,
                    "prohibit_login": false,
                    "location": "",
                    "website": "",
                    "description": "",
                    "visibility": "private",
                    "followers_count": 0,
                    "following_count": 0,
                    "starred_repos_count": 0,
                    "username": "foo"
                  },
                  "name": "baz",
                  "full_name": "foo/baz",
                  "description": "",
                  "empty": false,
                  "private": false,
                  "fork": false,
                  "template": false,
                  "parent": null,
                  "mirror": false,
                  "size": 105,
                  "language": "",
                  "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
                  "html_url": "https://git.example.com/foo/baz",
                  "ssh_url": "forgejo@git.example.com:foo/baz.git",
                  "clone_url": "https://git.example.com/foo/baz.git",
                  "original_url": "",
                  "website": "",
                  "stars_count": 0,
                  "forks_count": 0,
                  "watchers_count": 1,
                  "open_issues_count": 0,
                  "open_pr_counter": 1,
                  "release_counter": 0,
                  "default_branch": "main",
                  "archived": false,
                  "created_at": "2023-02-07T16:52:32+09:00",
                  "updated_at": "2023-02-07T16:58:43+09:00",
                  "permissions": {
                    "admin": true,
                    "push": true,
                    "pull": true
                  },
                  "has_issues": true,
                  "internal_tracker": {
                    "enable_time_tracker": true,
                    "allow_only_contributors_to_track_time": true,
                    "enable_issue_dependencies": true
                  },
                  "has_wiki": true,
                  "has_pull_requests": true,
                  "has_projects": true,
                  "ignore_whitespace_conflicts": false,
                  "allow_merge_commits": true,
                  "allow_rebase": true,
                  "allow_rebase_explicit": true,
                  "allow_squash_merge": true,
                  "allow_rebase_update": true,
                  "default_delete_branch_after_merge": false,
                  "default_merge_style": "merge",
                  "avatar_url": "",
                  "internal": true,
                  "mirror_interval": "",
                  "mirror_updated": "0001-01-01T00:00:00Z",
                  "repo_transfer": null
                }
              },
              "merge_base": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
              "due_date": null,
              "created_at": "2023-02-07T16:59:26+09:00",
              "updated_at": "2023-02-07T16:59:27+09:00",
              "closed_at": null
            }
          ] """

  def closePull1 =
    json""" {
            "id": 1,
            "url": "https://git.example.com/foo/baz/pulls/1",
            "number": 1,
            "user": {
              "id": 2,
              "login": "foo",
              "login_name": "",
              "full_name": "",
              "email": "foo@example.com",
              "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
              "language": "",
              "is_admin": false,
              "last_login": "0001-01-01T00:00:00Z",
              "created": "2023-01-25T21:48:28+09:00",
              "restricted": false,
              "active": false,
              "prohibit_login": false,
              "location": "",
              "website": "",
              "description": "",
              "visibility": "private",
              "followers_count": 0,
              "following_count": 0,
              "starred_repos_count": 0,
              "username": "foo"
            },
            "title": "update README",
            "body": "",
            "labels": [],
            "milestone": null,
            "assignee": null,
            "assignees": null,
            "state": "closed",
            "is_locked": false,
            "comments": 0,
            "html_url": "https://git.example.com/foo/baz/pulls/1",
            "diff_url": "https://git.example.com/foo/baz/pulls/1.diff",
            "patch_url": "https://git.example.com/foo/baz/pulls/1.patch",
            "mergeable": true,
            "merged": false,
            "merged_at": null,
            "merge_commit_sha": null,
            "merged_by": null,
            "allow_maintainer_edit": false,
            "base": {
              "label": "main",
              "ref": "main",
              "sha": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
              "repo_id": 5,
              "repo": {
                "id": 5,
                "owner": {
                  "id": 2,
                  "login": "foo",
                  "login_name": "",
                  "full_name": "",
                  "email": "foo@example.com",
                  "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
                  "language": "",
                  "is_admin": false,
                  "last_login": "0001-01-01T00:00:00Z",
                  "created": "2023-01-25T21:48:28+09:00",
                  "restricted": false,
                  "active": false,
                  "prohibit_login": false,
                  "location": "",
                  "website": "",
                  "description": "",
                  "visibility": "private",
                  "followers_count": 0,
                  "following_count": 0,
                  "starred_repos_count": 0,
                  "username": "foo"
                },
                "name": "baz",
                "full_name": "foo/baz",
                "description": "",
                "empty": false,
                "private": false,
                "fork": false,
                "template": false,
                "parent": null,
                "mirror": false,
                "size": 105,
                "language": "",
                "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
                "html_url": "https://git.example.com/foo/baz",
                "ssh_url": "forgejo@git.example.com:foo/baz.git",
                "clone_url": "https://git.example.com/foo/baz.git",
                "original_url": "",
                "website": "",
                "stars_count": 0,
                "forks_count": 0,
                "watchers_count": 1,
                "open_issues_count": 0,
                "open_pr_counter": 0,
                "release_counter": 0,
                "default_branch": "main",
                "archived": false,
                "created_at": "2023-02-07T16:52:32+09:00",
                "updated_at": "2023-02-07T16:58:43+09:00",
                "permissions": {
                  "admin": true,
                  "push": true,
                  "pull": true
                },
                "has_issues": true,
                "internal_tracker": {
                  "enable_time_tracker": true,
                  "allow_only_contributors_to_track_time": true,
                  "enable_issue_dependencies": true
                },
                "has_wiki": true,
                "has_pull_requests": true,
                "has_projects": true,
                "ignore_whitespace_conflicts": false,
                "allow_merge_commits": true,
                "allow_rebase": true,
                "allow_rebase_explicit": true,
                "allow_squash_merge": true,
                "allow_rebase_update": true,
                "default_delete_branch_after_merge": false,
                "default_merge_style": "merge",
                "avatar_url": "",
                "internal": true,
                "mirror_interval": "",
                "mirror_updated": "0001-01-01T00:00:00Z",
                "repo_transfer": null
              }
            },
            "head": {
              "label": "qux",
              "ref": "qux",
              "sha": "291355f22ea6e0179f85ee5ace4af67ab61e0adb",
              "repo_id": 5,
              "repo": {
                "id": 5,
                "owner": {
                  "id": 2,
                  "login": "foo",
                  "login_name": "",
                  "full_name": "",
                  "email": "foo@example.com",
                  "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
                  "language": "",
                  "is_admin": false,
                  "last_login": "0001-01-01T00:00:00Z",
                  "created": "2023-01-25T21:48:28+09:00",
                  "restricted": false,
                  "active": false,
                  "prohibit_login": false,
                  "location": "",
                  "website": "",
                  "description": "",
                  "visibility": "private",
                  "followers_count": 0,
                  "following_count": 0,
                  "starred_repos_count": 0,
                  "username": "foo"
                },
                "name": "baz",
                "full_name": "foo/baz",
                "description": "",
                "empty": false,
                "private": false,
                "fork": false,
                "template": false,
                "parent": null,
                "mirror": false,
                "size": 105,
                "language": "",
                "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
                "html_url": "https://git.example.com/foo/baz",
                "ssh_url": "forgejo@git.example.com:foo/baz.git",
                "clone_url": "https://git.example.com/foo/baz.git",
                "original_url": "",
                "website": "",
                "stars_count": 0,
                "forks_count": 0,
                "watchers_count": 1,
                "open_issues_count": 0,
                "open_pr_counter": 0,
                "release_counter": 0,
                "default_branch": "main",
                "archived": false,
                "created_at": "2023-02-07T16:52:32+09:00",
                "updated_at": "2023-02-07T16:58:43+09:00",
                "permissions": {
                  "admin": true,
                  "push": true,
                  "pull": true
                },
                "has_issues": true,
                "internal_tracker": {
                  "enable_time_tracker": true,
                  "allow_only_contributors_to_track_time": true,
                  "enable_issue_dependencies": true
                },
                "has_wiki": true,
                "has_pull_requests": true,
                "has_projects": true,
                "ignore_whitespace_conflicts": false,
                "allow_merge_commits": true,
                "allow_rebase": true,
                "allow_rebase_explicit": true,
                "allow_squash_merge": true,
                "allow_rebase_update": true,
                "default_delete_branch_after_merge": false,
                "default_merge_style": "merge",
                "avatar_url": "",
                "internal": true,
                "mirror_interval": "",
                "mirror_updated": "0001-01-01T00:00:00Z",
                "repo_transfer": null
              }
            },
            "merge_base": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
            "due_date": null,
            "created_at": "2023-02-07T16:59:26+09:00",
            "updated_at": "2023-02-07T17:11:22+09:00",
            "closed_at": null
          } """

  def commentPR1 =
    json""" {
            "id": 138,
            "html_url": "https://git.example.com/foo/baz/pulls/1#issuecomment-138",
            "pull_request_url": "https://git.example.com/foo/baz/pulls/1",
            "issue_url": "",
            "user": {
              "id": 2,
              "login": "foo",
              "login_name": "",
              "full_name": "",
              "email": "foo@example.com",
              "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
              "language": "",
              "is_admin": false,
              "last_login": "0001-01-01T00:00:00Z",
              "created": "2023-01-25T21:48:28+09:00",
              "restricted": false,
              "active": false,
              "prohibit_login": false,
              "location": "",
              "website": "",
              "description": "",
              "visibility": "private",
              "followers_count": 0,
              "following_count": 0,
              "starred_repos_count": 0,
              "username": "foo"
            },
            "original_author": "",
            "original_author_id": 0,
            "body": "hi",
            "created_at": "2023-02-07T17:20:57+09:00",
            "updated_at": "2023-02-07T17:20:57+09:00"
          } """

  def createPR2 =
    json""" {
      "id": 2,
      "url": "https://git.example.com/foo/baz/pulls/2",
      "number": 2,
      "user": {
        "id": 2,
        "login": "foo",
        "login_name": "",
        "full_name": "",
        "email": "foo@example.com",
        "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
        "language": "",
        "is_admin": false,
        "last_login": "0001-01-01T00:00:00Z",
        "created": "2023-01-25T21:48:28+09:00",
        "restricted": false,
        "active": false,
        "prohibit_login": false,
        "location": "",
        "website": "",
        "description": "",
        "visibility": "private",
        "followers_count": 0,
        "following_count": 0,
        "starred_repos_count": 0,
        "username": "foo"
      },
      "title": "pr test",
      "body": "hi",
      "labels": [],
      "milestone": null,
      "assignee": null,
      "assignees": null,
      "state": "open",
      "is_locked": false,
      "comments": 0,
      "html_url": "https://git.example.com/foo/baz/pulls/2",
      "diff_url": "https://git.example.com/foo/baz/pulls/2.diff",
      "patch_url": "https://git.example.com/foo/baz/pulls/2.patch",
      "mergeable": true,
      "merged": false,
      "merged_at": null,
      "merge_commit_sha": null,
      "merged_by": null,
      "allow_maintainer_edit": false,
      "base": {
        "label": "main",
        "ref": "main",
        "sha": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
        "repo_id": 5,
        "repo": {
          "id": 5,
          "owner": {
            "id": 2,
            "login": "foo",
            "login_name": "",
            "full_name": "",
            "email": "foo@example.com",
            "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
            "language": "",
            "is_admin": false,
            "last_login": "0001-01-01T00:00:00Z",
            "created": "2023-01-25T21:48:28+09:00",
            "restricted": false,
            "active": false,
            "prohibit_login": false,
            "location": "",
            "website": "",
            "description": "",
            "visibility": "private",
            "followers_count": 0,
            "following_count": 0,
            "starred_repos_count": 0,
            "username": "foo"
          },
          "name": "baz",
          "full_name": "foo/baz",
          "description": "",
          "empty": false,
          "private": false,
          "fork": false,
          "template": false,
          "parent": null,
          "mirror": false,
          "size": 25,
          "language": "",
          "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
          "html_url": "https://git.example.com/foo/baz",
          "ssh_url": "forgejo@git.example.com:foo/baz.git",
          "clone_url": "https://git.example.com/foo/baz.git",
          "original_url": "",
          "website": "",
          "stars_count": 0,
          "forks_count": 1,
          "watchers_count": 1,
          "open_issues_count": 0,
          "open_pr_counter": 1,
          "release_counter": 0,
          "default_branch": "main",
          "archived": false,
          "created_at": "2023-02-07T16:52:32+09:00",
          "updated_at": "2023-02-07T17:57:49+09:00",
          "permissions": {
            "admin": true,
            "push": true,
            "pull": true
          },
          "has_issues": true,
          "internal_tracker": {
            "enable_time_tracker": true,
            "allow_only_contributors_to_track_time": true,
            "enable_issue_dependencies": true
          },
          "has_wiki": true,
          "has_pull_requests": true,
          "has_projects": true,
          "ignore_whitespace_conflicts": false,
          "allow_merge_commits": true,
          "allow_rebase": true,
          "allow_rebase_explicit": true,
          "allow_squash_merge": true,
          "allow_rebase_update": true,
          "default_delete_branch_after_merge": false,
          "default_merge_style": "merge",
          "avatar_url": "",
          "internal": true,
          "mirror_interval": "",
          "mirror_updated": "0001-01-01T00:00:00Z",
          "repo_transfer": null
        }
      },
      "head": {
        "label": "pr2",
        "ref": "pr2",
        "sha": "7301e317e8482ad612784f6204206a0204f94aa7",
        "repo_id": 5,
        "repo": {
          "id": 5,
          "owner": {
            "id": 2,
            "login": "foo",
            "login_name": "",
            "full_name": "",
            "email": "foo@example.com",
            "avatar_url": "https://secure.gravatar.com/avatar/b48def645758b95537d4424c84d1a9ff?d=identicon",
            "language": "",
            "is_admin": false,
            "last_login": "0001-01-01T00:00:00Z",
            "created": "2023-01-25T21:48:28+09:00",
            "restricted": false,
            "active": false,
            "prohibit_login": false,
            "location": "",
            "website": "",
            "description": "",
            "visibility": "private",
            "followers_count": 0,
            "following_count": 0,
            "starred_repos_count": 0,
            "username": "foo"
          },
          "name": "baz",
          "full_name": "foo/baz",
          "description": "",
          "empty": false,
          "private": false,
          "fork": false,
          "template": false,
          "parent": null,
          "mirror": false,
          "size": 25,
          "language": "",
          "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
          "html_url": "https://git.example.com/foo/baz",
          "ssh_url": "forgejo@git.example.com:foo/baz.git",
          "clone_url": "https://git.example.com/foo/baz.git",
          "original_url": "",
          "website": "",
          "stars_count": 0,
          "forks_count": 1,
          "watchers_count": 1,
          "open_issues_count": 0,
          "open_pr_counter": 1,
          "release_counter": 0,
          "default_branch": "main",
          "archived": false,
          "created_at": "2023-02-07T16:52:32+09:00",
          "updated_at": "2023-02-07T17:57:49+09:00",
          "permissions": {
            "admin": true,
            "push": true,
            "pull": true
          },
          "has_issues": true,
          "internal_tracker": {
            "enable_time_tracker": true,
            "allow_only_contributors_to_track_time": true,
            "enable_issue_dependencies": true
          },
          "has_wiki": true,
          "has_pull_requests": true,
          "has_projects": true,
          "ignore_whitespace_conflicts": false,
          "allow_merge_commits": true,
          "allow_rebase": true,
          "allow_rebase_explicit": true,
          "allow_squash_merge": true,
          "allow_rebase_update": true,
          "default_delete_branch_after_merge": false,
          "default_merge_style": "merge",
          "avatar_url": "",
          "internal": true,
          "mirror_interval": "",
          "mirror_updated": "0001-01-01T00:00:00Z",
          "repo_transfer": null
        }
      },
      "merge_base": "6b5ec7e2b6eaf45ecb654a9187e1f5874210fca3",
      "due_date": null,
      "created_at": "2023-02-07T17:59:48+09:00",
      "updated_at": "2023-02-07T17:59:49+09:00",
      "closed_at": null
    } """

  def listLabels =
    json"""
       [
        {
          "id": 2,
          "name": "label1",
          "color": "e01060",
          "description": "",
          "url": "https://git.example.com/api/v1/repos/foo/baz/labels/2"
        }
      ]
        """

  def createLabel =
    json"""
        {
          "id": 2,
          "name": "label1",
          "color": "e01060",
          "description": "",
          "url": "https://git.example.com/api/v1/repos/foo/baz/labels/2"
        }
        """

  def forkRepo =
    json"""
          {
      "id": 3,
      "owner": {
        "id": 3,
        "login": "bar",
        "login_name": "",
        "full_name": "",
        "email": "bar@example.com",
        "avatar_url": "https://git.example.com/avatars/7b12520feeef7972fc17e137111da1e7",
        "language": "",
        "is_admin": false,
        "last_login": "0001-01-01T00:00:00Z",
        "created": "2023-02-08T02:19:35Z",
        "restricted": false,
        "active": false,
        "prohibit_login": false,
        "location": "",
        "website": "",
        "description": "",
        "visibility": "public",
        "followers_count": 0,
        "following_count": 0,
        "starred_repos_count": 0,
        "username": "bar"
      },
      "name": "baz",
      "full_name": "bar/baz",
      "description": "",
      "empty": false,
      "private": false,
      "fork": true,
      "template": false,
      "parent": {
        "id": 1,
        "owner": {
          "id": 2,
          "login": "foo",
          "login_name": "",
          "full_name": "",
          "email": "foo@example.local",
          "avatar_url": "https://git.example.com/avatars/dc366aecd29705111fcea5d94b393388",
          "language": "",
          "is_admin": false,
          "last_login": "0001-01-01T00:00:00Z",
          "created": "2023-02-08T02:19:26Z",
          "restricted": false,
          "active": false,
          "prohibit_login": false,
          "location": "",
          "website": "",
          "description": "",
          "visibility": "public",
          "followers_count": 0,
          "following_count": 0,
          "starred_repos_count": 0,
          "username": "foo"
        },
        "name": "baz",
        "full_name": "foo/baz",
        "description": "",
        "empty": false,
        "private": false,
        "fork": false,
        "template": false,
        "parent": null,
        "mirror": false,
        "size": 25,
        "language": "",
        "languages_url": "https://git.example.com/api/v1/repos/foo/baz/languages",
        "html_url": "https://git.example.com/foo/baz",
        "ssh_url": "gitea@git.example.com:foo/baz.git",
        "clone_url": "https://git.example.com/foo/baz.git",
        "original_url": "",
        "website": "",
        "stars_count": 0,
        "forks_count": 1,
        "watchers_count": 1,
        "open_issues_count": 0,
        "open_pr_counter": 0,
        "release_counter": 0,
        "default_branch": "main",
        "archived": false,
        "created_at": "2023-02-08T02:20:02Z",
        "updated_at": "2023-02-08T02:22:05Z",
        "permissions": {
          "admin": true,
          "push": true,
          "pull": true
        },
        "has_issues": true,
        "internal_tracker": {
          "enable_time_tracker": true,
          "allow_only_contributors_to_track_time": true,
          "enable_issue_dependencies": true
        },
        "has_wiki": true,
        "has_pull_requests": true,
        "has_projects": true,
        "ignore_whitespace_conflicts": false,
        "allow_merge_commits": true,
        "allow_rebase": true,
        "allow_rebase_explicit": true,
        "allow_squash_merge": true,
        "allow_rebase_update": true,
        "default_delete_branch_after_merge": false,
        "default_merge_style": "merge",
        "avatar_url": "",
        "internal": false,
        "mirror_interval": "",
        "mirror_updated": "0001-01-01T00:00:00Z",
        "repo_transfer": null
      },
      "mirror": false,
      "size": 0,
      "language": "",
      "languages_url": "https://git.example.com/api/v1/repos/bar/baz/languages",
      "html_url": "https://git.example.com/bar/baz",
      "ssh_url": "gitea@git.example.com:bar/baz.git",
      "clone_url": "https://git.example.com/bar/baz.git",
      "original_url": "",
      "website": "",
      "stars_count": 0,
      "forks_count": 0,
      "watchers_count": 0,
      "open_issues_count": 0,
      "open_pr_counter": 0,
      "release_counter": 0,
      "default_branch": "main",
      "archived": false,
      "created_at": "2023-02-08T02:23:28Z",
      "updated_at": "2023-02-08T02:23:28Z",
      "permissions": {
        "admin": true,
        "push": true,
        "pull": true
      },
      "has_issues": true,
      "internal_tracker": {
        "enable_time_tracker": true,
        "allow_only_contributors_to_track_time": true,
        "enable_issue_dependencies": true
      },
      "has_wiki": true,
      "has_pull_requests": true,
      "has_projects": true,
      "ignore_whitespace_conflicts": false,
      "allow_merge_commits": true,
      "allow_rebase": true,
      "allow_rebase_explicit": true,
      "allow_squash_merge": true,
      "allow_rebase_update": true,
      "default_delete_branch_after_merge": false,
      "default_merge_style": "merge",
      "avatar_url": "",
      "internal": false,
      "mirror_interval": "",
      "mirror_updated": "0001-01-01T00:00:00Z",
      "repo_transfer": null
    } """
}
