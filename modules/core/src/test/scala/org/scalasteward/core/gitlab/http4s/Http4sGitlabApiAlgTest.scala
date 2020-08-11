package org.scalasteward.core.gitlab.http4s

import cats.effect.IO
import io.circe.literal._
import io.circe.parser._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockContext.{config, user}
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.{HttpJsonClient, Nel}
import org.scalasteward.core.vcs.data._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class Http4sGitlabApiAlgTest extends AnyFunSuite with Matchers {
  import GitlabJsonCodec._

  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "projects" / "foo/bar" =>
        Ok(getRepo)

      case POST -> Root / "projects" / "scala-steward/bar" / "merge_requests" =>
        Ok(getMr)

      case POST -> Root / "projects" / "foo/bar" / "merge_requests" =>
        Ok(
          getMr.deepMerge(
            json""" { "iid": 150, "web_url": "https://gitlab.com/foo/bar/merge_requests/150" } """
          )
        )

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  val gitlabApiAlg =
    new Http4sGitLabApiAlg[IO](config.vcsApiHost, user, _ => IO.pure, doNotFork = false)

  val data = UpdateData(
    Repo("foo", "bar"),
    Repo("scala-steward", "bar"),
    RepoConfig(),
    Update.Single("ch.qos.logback" % "logback-classic" % "1.2.0", Nel.of("1.2.3")),
    Branch("master"),
    Sha1(Sha1.HexString("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
    Branch("update/logback-classic-1.2.3")
  )
  val newPRData =
    NewPullRequestData.from(data, "scala-steward:update/logback-classic-1.2.3")

  test("createPullRequest") {
    val prOut =
      gitlabApiAlg
        .createPullRequest(Repo("foo", "bar"), newPRData)
        .unsafeRunSync()

    prOut shouldBe PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/7115",
      PullRequestState.Open,
      "title"
    )
  }

  test("createPullRequest -- no fork") {
    val gitlabApiAlgNoFork =
      new Http4sGitLabApiAlg[IO](config.vcsApiHost, user, _ => IO.pure, doNotFork = true)
    val prOut =
      gitlabApiAlgNoFork
        .createPullRequest(Repo("foo", "bar"), newPRData)
        .unsafeRunSync()

    prOut shouldBe PullRequestOut(
      uri"https://gitlab.com/foo/bar/merge_requests/150",
      PullRequestState.Open,
      "title"
    )
  }

  test("extractProjectId") {
    decode[ProjectId](getRepo.spaces2) shouldBe Right(ProjectId(12414871L))
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
      "labels": [],
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
}
