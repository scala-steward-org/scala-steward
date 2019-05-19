package org.scalasteward.core.gitlab.http4s

import io.circe.parser._
import org.scalatest.{FunSuite, Matchers}

class Http4sGitlabApiAlgTest extends FunSuite with Matchers {
  import GitlabJsonCodec._

  test("extractProjectId") {
    decode[ProjectId](getRepo) shouldBe Right(ProjectId(12414871L))
  }

  val getRepo = s"""
    {
        "id": 12414871,
        "description": "",
        "name": "scala-steward-test",
        "name_with_namespace": "David Francoeur / scala-steward-test",
        "path": "scala-steward-test",
        "path_with_namespace": "daddykotex/scala-steward-test",
        "created_at": "2019-05-20T01:40:25.772Z",
        "default_branch": "master",
        "tag_list": [],
        "ssh_url_to_repo": "git@gitlab.com:daddykotex/scala-steward-test.git",
        "http_url_to_repo": "https://gitlab.com/daddykotex/scala-steward-test.git",
        "web_url": "https://gitlab.com/daddykotex/scala-steward-test",
        "readme_url": "https://gitlab.com/daddykotex/scala-steward-test/blob/master/README.md",
        "avatar_url": null,
        "star_count": 0,
        "forks_count": 0,
        "last_activity_at": "2019-05-20T04:31:10.797Z",
        "namespace": {
          "id": 5239389,
          "name": "daddykotex",
          "path": "daddykotex",
          "kind": "user",
          "full_path": "daddykotex",
          "parent_id": null,
          "avatar_url": "https://secure.gravatar.com/avatar/ff6267e9bc721e1265fbb3e464896197?s=80&d=identicon",
          "web_url": "https://gitlab.com/daddykotex"
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
          "name": "David Francoeur",
          "username": "daddykotex",
          "state": "active",
          "avatar_url": "https://secure.gravatar.com/avatar/ff6267e9bc721e1265fbb3e464896197?s=80&d=identicon",
          "web_url": "https://gitlab.com/daddykotex"
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
