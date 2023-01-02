package org.scalasteward.core.vcs.gitlab

import cats.effect.unsafe.implicits.global
import cats.effect.{Concurrent, IO}
import cats.implicits._
import io.circe.Json
import io.circe.literal._
import io.circe.parser._
import munit.FunSuite
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.Allow
import org.http4s.implicits._
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.Config.GitLabCfg
import org.scalasteward.core.data.{RepoData, UpdateData}
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.gitlab.GitLabJsonCodec._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class GitLabApiAlgTest extends FunSuite {
  object MergeWhenPipelineSucceedsMatcher
      extends QueryParamDecoderMatcher[Boolean]("merge_when_pipeline_succeeds")

  object RequiredReviewersMatcher extends QueryParamDecoderMatcher[Int]("approvals_required")

  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case POST -> Root / "projects" / "foo/bar" / "fork" =>
        Ok(getRepo)

      case GET -> Root / "projects" / "foo/bar" =>
        Ok(getRepo)

      case GET -> Root / "projects" / "foo/bar" / "repository" / "branches" / "master" =>
        Ok(json"""{
                    "name": "master",
                    "commit": {
                      "id": "07eb2a203e297c8340273950e98b2cab68b560c1"
                    }
                  }""")

      case POST -> Root / "projects" / s"${config.vcsCfg.login}/bar" / "merge_requests" =>
        Ok(getMr)

      case GET -> Root / "projects" / "foo/bar" / "merge_requests" =>
        Ok(Json.arr(getMr))

      case POST -> Root / "projects" / "foo/bar" / "merge_requests" =>
        Ok(
          getMr.deepMerge(
            json""" { "iid": 150, "web_url": "https://gitlab.com/foo/bar/merge_requests/150" } """
          )
        )

      case GET -> Root / "projects" / "foo/bar" / "merge_requests" / "150" =>
        Ok(
          getMr.deepMerge(
            json""" { "iid": 150, "web_url": "https://gitlab.com/foo/bar/merge_requests/150" } """
          )
        )

      case PUT -> Root / "projects" / "foo/bar" / "merge_requests" / IntVar(_) =>
        Ok(
          getMr.deepMerge(
            json""" { "state": "closed" } """
          )
        )

      case PUT -> Root / "projects" / "foo/bar" / "merge_requests" / "150" / "merge"
          :? MergeWhenPipelineSucceedsMatcher(_) =>
        Ok(
          getMr.deepMerge(
            json""" { "iid": 150, "web_url": "https://gitlab.com/foo/bar/merge_requests/150" } """
          )
        )

      case PUT -> Root / "projects" / "foo/bar" / "merge_requests" / "150" / "approvals"
          :? RequiredReviewersMatcher(requiredApprovers) =>
        Ok(
          putMrApprovals.deepMerge(
            json""" { "iid": 150, "approvals_required": $requiredApprovers, "web_url": "https://gitlab.com/foo/bar/merge_requests/150" } """
          )
        )

      case POST -> Root / "projects" / "foo/bar" / "merge_requests" / "150" / "notes" =>
        Ok(json"""  {
            "body": "Superseded by #1234"
          } """)

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  val gitlabApiAlg =
    new GitLabApiAlg[IO](
      config.vcsCfg,
      GitLabCfg(mergeWhenPipelineSucceeds = false, requiredReviewers = None),
      _ => IO.pure
    )

  private val data = UpdateData(
    RepoData(Repo("foo", "bar"), dummyRepoCache, RepoConfig.empty),
    Repo("scala-steward", "bar"),
    ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
    Branch("master"),
    Sha1(Sha1.HexString.unsafeFrom("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
    Branch("update/logback-classic-1.2.3")
  )
  private val newPRData =
    NewPullRequestData.from(data, "scala-steward:update/logback-classic-1.2.3")

  test("createFork") {
    val repoOut = gitlabApiAlg.createFork(Repo("foo", "bar")).unsafeRunSync()
    val expected = RepoOut(
      name = "bar",
      owner = UserOut("foo"),
      parent = None,
      clone_url = uri"https://gitlab.com/foo/bar.git",
      default_branch = Branch("master")
    )
    assertEquals(repoOut, expected)
  }

  test("getRepo") {
    val repoOut = gitlabApiAlg.getRepo(Repo("foo", "bar")).unsafeRunSync()
    val expected = RepoOut(
      name = "bar",
      owner = UserOut("foo"),
      parent = None,
      clone_url = uri"https://gitlab.com/foo/bar.git",
      default_branch = Branch("master")
    )
    assertEquals(repoOut, expected)
  }

  test("getBranch") {
    val branchOut = gitlabApiAlg.getBranch(Repo("foo", "bar"), Branch("master")).unsafeRunSync()
    val expected = BranchOut(
      Branch("master"),
      CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
    )
    assertEquals(branchOut, expected)
  }

  test("createPullRequest") {
    val prOut = gitlabApiAlg
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()
    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/7115",
      PullRequestState.Open,
      PullRequestNumber(7115),
      "title"
    )
    assertEquals(prOut, expected)
  }

  test("createPullRequest -- no fork") {
    val gitlabApiAlgNoFork = new GitLabApiAlg[IO](
      config.vcsCfg.copy(doNotFork = true),
      GitLabCfg(mergeWhenPipelineSucceeds = false, requiredReviewers = None),
      _ => IO.pure
    )
    val prOut = gitlabApiAlgNoFork
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()
    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      PullRequestNumber(150),
      "title"
    )
    assertEquals(prOut, expected)
  }

  test("extractProjectId") {
    assertEquals(decode[ProjectId](getRepo.spaces2), Right(ProjectId(12414871L)))
  }

  test("closePullRequest") {
    val prOut = gitlabApiAlg
      .closePullRequest(Repo("foo", "bar"), PullRequestNumber(7115))
      .unsafeRunSync()
    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/7115",
      PullRequestState.Closed,
      PullRequestNumber(7115),
      "title"
    )
    assertEquals(prOut, expected)
  }

  test("createPullRequest -- auto merge") {
    val gitlabApiAlgNoFork = new GitLabApiAlg[IO](
      config.vcsCfg.copy(doNotFork = true),
      GitLabCfg(mergeWhenPipelineSucceeds = true, requiredReviewers = None),
      _ => IO.pure
    )

    val prOut = gitlabApiAlgNoFork
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()

    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      PullRequestNumber(150),
      "title"
    )

    assertEquals(prOut, expected)
  }

  test("createPullRequest -- don't fail on error code") {
    val errorClient: Client[IO] =
      Client.fromHttpApp(
        (HttpRoutes.of[IO] {
          case PUT -> Root / "projects" / "foo/bar" / "merge_requests" / "150" / "merge"
              :? MergeWhenPipelineSucceedsMatcher(_) =>
            MethodNotAllowed(Allow(OPTIONS, GET, HEAD))
        } <+> routes).orNotFound
      )
    val errorJsonClient: HttpJsonClient[IO] =
      new HttpJsonClient[IO]()(errorClient, Concurrent[IO])

    val gitlabApiAlgNoFork = new GitLabApiAlg[IO](
      config.vcsCfg.copy(doNotFork = true),
      GitLabCfg(mergeWhenPipelineSucceeds = true, requiredReviewers = None),
      _ => IO.pure
    )(errorJsonClient, implicitly, implicitly)

    val prOut = gitlabApiAlgNoFork
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()

    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      PullRequestNumber(150),
      "title"
    )

    assertEquals(prOut, expected)
  }

  test("createPullRequest -- reduce required reviewers") {
    val gitlabApiAlgLessReviewersRequired = new GitLabApiAlg[IO](
      config.vcsCfg.copy(doNotFork = true),
      GitLabCfg(mergeWhenPipelineSucceeds = true, requiredReviewers = Some(0)),
      _ => IO.pure
    )

    val prOut = gitlabApiAlgLessReviewersRequired
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()

    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      PullRequestNumber(150),
      "title"
    )

    assertEquals(prOut, expected)
  }

  test("createPullRequest -- no fail upon required reviewers error") {
    val errorClient: Client[IO] =
      Client.fromHttpApp(
        (HttpRoutes.of[IO] {
          case PUT -> Root / "projects" / "foo/bar" / "merge_requests" / "150" / "approvals"
              :? RequiredReviewersMatcher(requiredReviewers) =>
            BadRequest(s"Cannot set requiredReviewers to $requiredReviewers")
        } <+> routes).orNotFound
      )
    val errorJsonClient: HttpJsonClient[IO] =
      new HttpJsonClient[IO]()(errorClient, Concurrent[IO])

    val gitlabApiAlgLessReviewersRequiredNoError = new GitLabApiAlg[IO](
      config.vcsCfg.copy(doNotFork = true),
      GitLabCfg(mergeWhenPipelineSucceeds = true, requiredReviewers = Some(0)),
      _ => IO.pure
    )(errorJsonClient, implicitly, implicitly)

    val prOut = gitlabApiAlgLessReviewersRequiredNoError
      .createPullRequest(Repo("foo", "bar"), newPRData)
      .unsafeRunSync()

    val expected = PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      PullRequestNumber(150),
      "title"
    )

    assertEquals(prOut, expected)
  }

  test("referencePullRequest") {
    val reference = gitlabApiAlg.referencePullRequest(PullRequestNumber(1347))
    assertEquals(reference, "!1347")
  }

  test("commentPullRequest") {
    val comment = gitlabApiAlg
      .commentPullRequest(Repo("foo", "bar"), PullRequestNumber(150), "Superseded by #1234")
      .unsafeRunSync()
    assertEquals(comment, Comment("Superseded by #1234"))
  }

  test("listPullRequests") {

    val prs =
      gitlabApiAlg.listPullRequests(Repo("foo", "bar"), "head", Branch("pr-123")).unsafeRunSync()
    assertEquals(
      prs,
      List(
        PullRequestOut(
          uri"https://gitlab.com/foo/bar/merge_requests/7115",
          PullRequestState.Open,
          PullRequestNumber(7115),
          "title"
        )
      )
    )
  }

  test("labelPullRequest") {
    val result = gitlabApiAlg
      .labelPullRequest(Repo("foo", "bar"), PullRequestNumber(150), List("A", "B"))
      .attempt
      .unsafeRunSync()
    assert(result.isRight)
  }

  val getMr = json"""
    {
      "id": 26328,
      "iid": 7115,
      "project_id": 466,
      "title": "title",
      "description": "description",
      "state": "opened",
      "created_at": "2019-07-18T14:38:41.965Z",
      "updated_at": "2019-07-18T14:41:33.675Z",
      "merged_by": null,
      "merged_at": null,
      "closed_by": null,
      "closed_at": null,
      "target_branch": "master",
      "source_branch": "update/logback-classic-1.2.3",
      "user_notes_count": 0,
      "upvotes": 1,
      "downvotes": 0,
      "assignee": {
        "id": 71,
        "name": "Some Assignee",
        "username": "other_username",
        "state": "active",
        "avatar_url": "https://gitlab.com/uploads/-/system/user/avatar/71/AAEAAQAAAAAAAAjgAAAAJDNjNzRiNzFhLTBjMTMtNDI0Ny04OTZmLTE2NmE5NzJiOWQyNw.jpeg",
        "web_url": "https://gitlab.com/other_username"
      },
      "author": {
        "id": 2,
        "name": "Jeremy Attali",
        "username": "jattali",
        "state": "active",
        "avatar_url": "https://gitlab.com/uploads/-/system/user/avatar/2/android.png",
        "web_url": "https://gitlab.com/jattali"
      },
      "assignees": [
        {
          "id": 71,
          "name": "Some Assignee",
          "username": "other_username",
          "state": "active",
          "avatar_url": "https://gitlab.com/uploads/-/system/user/avatar/71/AAEAAQAAAAAAAAjgAAAAJDNjNzRiNzFhLTBjMTMtNDI0Ny04OTZmLTE2NmE5NzJiOWQyNw.jpeg",
          "web_url": "https://gitlab.com/other_username"
        }
      ],
      "source_project_id": 466,
      "target_project_id": 466,
      "labels": ["A", "B"],
      "work_in_progress": false,
      "milestone": null,
      "merge_when_pipeline_succeeds": true,
      "merge_status": "can_be_merged",
      "sha": "138522e6230feeed4b691d33c2ee9bbfabef7050",
      "merge_commit_sha": null,
      "discussion_locked": null,
      "should_remove_source_branch": true,
      "force_remove_source_branch": true,
      "reference": "!7115",
      "web_url": "https://gitlab.com/foo/bar/merge_requests/7115",
      "time_stats": {
        "time_estimate": 0,
        "total_time_spent": 0,
        "human_time_estimate": null,
        "human_total_time_spent": null
      },
      "squash": false,
      "task_completion_status": {
        "count": 0,
        "completed_count": 0
      }
    }
  """

  val getRepo = json"""
    {
        "id": 12414871,
        "description": "",
        "name": "bar",
        "name_with_namespace": "Some Owner / bar",
        "path": "bar",
        "path_with_namespace": "foo/bar",
        "created_at": "2019-05-20T01:40:25.772Z",
        "default_branch": "master",
        "tag_list": [],
        "ssh_url_to_repo": "git@gitlab.com:foo/bar.git",
        "http_url_to_repo": "https://gitlab.com/foo/bar.git",
        "web_url": "https://gitlab.com/foo/bar",
        "readme_url": "https://gitlab.com/foo/bar/blob/master/README.md",
        "avatar_url": null,
        "star_count": 0,
        "forks_count": 0,
        "last_activity_at": "2019-05-20T04:31:10.797Z",
        "namespace": {
          "id": 5239389,
          "name": "foo description",
          "path": "foo",
          "kind": "user",
          "full_path": "foo",
          "parent_id": null,
          "avatar_url": "https://secure.gravatar.com/avatar/ff6267e9bc721e1265fbb3e464896197?s=80&d=identicon",
          "web_url": "https://gitlab.com/foo"
        },
        "_links": {
          "self": "https://gitlab.com/api/v4/projects/12414871",
          "issues": "https://gitlab.com/api/v4/projects/12414871/issues",
          "merge_requests": "https://gitlab.com/api/v4/projects/12414871/merge_requests",
          "repo_branches": "https://gitlab.com/api/v4/projects/12414871/repository/branches",
          "labels": "https://gitlab.com/api/v4/projects/12414871/labels",
          "events": "https://gitlab.com/api/v4/projects/12414871/events",
          "members": "https://gitlab.com/api/v4/projects/12414871/members"
        },
        "archived": false,
        "visibility": "public",
        "owner": {
          "id": 4013687,
          "name": "Some Owner",
          "username": "foo",
          "state": "active",
          "avatar_url": "https://secure.gravatar.com/avatar/ff6267e9bc721e1265fbb3e464896197?s=80&d=identicon",
          "web_url": "https://gitlab.com/foo"
        },
        "resolve_outdated_diff_discussions": false,
        "container_registry_enabled": true,
        "issues_enabled": true,
        "merge_requests_enabled": true,
        "wiki_enabled": true,
        "jobs_enabled": true,
        "snippets_enabled": true,
        "shared_runners_enabled": true,
        "lfs_enabled": true,
        "creator_id": 4013687,
        "import_status": "none",
        "open_issues_count": 0,
        "public_jobs": true,
        "ci_config_path": null,
        "shared_with_groups": [],
        "only_allow_merge_if_pipeline_succeeds": false,
        "request_access_enabled": false,
        "only_allow_merge_if_all_discussions_are_resolved": false,
        "printing_merge_request_link_enabled": true,
        "merge_method": "merge",
        "external_authorization_classification_label": "",
        "permissions": {
          "project_access": null,
          "group_access": null
        },
        "approvals_before_merge": 0,
        "mirror": false,
        "packages_enabled": true
      }
    """

  val putMrApprovals =
    json"""
      {
        "id": 138691106,
        "iid": 4,
        "project_id": 466,
        "title": "Standard ScalaSteward configured MR title",
        "description": "Standard ScalaSteward configured MR description",
        "state": "opened",
        "created_at": "2022-02-04T17:43:57.363Z",
        "updated_at": "2022-02-11T22:38:28.645Z",
        "merge_status": "can_be_merged",
        "approved": true,
        "approvals_required": 0,
        "approvals_left": 0,
        "require_password_to_approve": false,
        "approved_by": [],
        "suggested_approvers": [],
        "approvers": [],
        "approver_groups": [],
        "user_has_approved": false,
        "user_can_approve": true,
        "approval_rules_left": [],
        "has_approval_rules": true,
        "merge_request_approvers_available": true,
        "multiple_approval_rules_available": true
     }
     """
}
