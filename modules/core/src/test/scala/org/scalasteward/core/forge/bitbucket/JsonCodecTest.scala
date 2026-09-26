/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.forge.bitbucket

import io.circe.Json
import io.circe.parser.*
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestOut, PullRequestState}

class JsonCodecTest extends FunSuite {
  test("PullRequestStatus decoding of expected values") {
    val mapping = Map(
      "OPEN" -> PullRequestState.Open,
      "MERGED" -> PullRequestState.Closed,
      "SUPERSEDED" -> PullRequestState.Closed,
      "DECLINED" -> PullRequestState.Closed
    )

    mapping.foreach { case (string, state) =>
      assertEquals(json.pullRequestStateDecoder.decodeJson(Json.fromString(string)), Right(state))
    }
  }

  test("PullRequestOut decoding") {
    val rawResponse = """
    {
      "rendered": {
        "description": {
          "raw": "",
          "markup": "markdown",
          "html": "",
          "type": "rendered"
        },
        "title": {
          "raw": "Dummy PR",
          "markup": "markdown",
          "html": "<p>Dummy PR</p>",
          "type": "rendered"
        }
      },
      "type": "pullrequest",
      "description": "",
      "links": {
        "decline": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/decline"
        },
        "diffstat": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/diffstat/acme-corp/my-scala-steward:ffbea2df0a61%0D71c045581b38?from_pullrequest_id=3"
        },
        "commits": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/commits"
        },
        "self": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3"
        },
        "comments": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/comments"
        },
        "merge": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/merge"
        },
        "html": {
          "href": "https://bitbucket.org/acme-corp/my-scala-steward/pull-requests/3"
        },
        "activity": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/activity"
        },
        "request-changes": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/request-changes"
        },
        "diff": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/diff/acme-corp/my-scala-steward:ffbea2df0a61%0D71c045581b38?from_pullrequest_id=3"
        },
        "approve": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/approve"
        },
        "statuses": {
          "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/pullrequests/3/statuses"
        }
      },
      "title": "Dummy PR",
      "close_source_branch": false,
      "reviewers": [],
      "id": 3,
      "destination": {
        "commit": {
          "hash": "71c045581b38",
          "type": "commit",
          "links": {
            "self": {
              "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/commit/71c045581b38"
            },
            "html": {
              "href": "https://bitbucket.org/acme-corp/my-scala-steward/commits/71c045581b38"
            }
          }
        },
        "repository": {
          "links": {
            "self": {
              "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward"
            },
            "html": {
              "href": "https://bitbucket.org/acme-corp/my-scala-steward"
            },
            "avatar": {
              "href": "https://bytebucket.org/ravatar/redacted"
            }
          },
          "type": "repository",
          "name": "my-scala-steward",
          "full_name": "acme-corp/my-scala-steward",
          "uuid": "redacted"
        },
        "branch": {
          "name": "master"
        }
      },
      "created_on": "2021-02-15T18:39:37.593077+00:00",
      "summary": {
        "raw": "",
        "markup": "markdown",
        "html": "",
        "type": "rendered"
      },
      "source": {
        "commit": {
          "hash": "ffbea2df0a61",
          "type": "commit",
          "links": {
            "self": {
              "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward/commit/ffbea2df0a61"
            },
            "html": {
              "href": "https://bitbucket.org/acme-corp/my-scala-steward/commits/ffbea2df0a61"
            }
          }
        },
        "repository": {
          "links": {
            "self": {
              "href": "https://api.bitbucket.org/2.0/repositories/acme-corp/my-scala-steward"
            },
            "html": {
              "href": "https://bitbucket.org/acme-corp/my-scala-steward"
            },
            "avatar": {
              "href": "https://bytebucket.org/ravatar/redacted"
            }
          },
          "type": "repository",
          "name": "my-scala-steward",
          "full_name": "acme-corp/my-scala-steward",
          "uuid": "redacted"
        },
        "branch": {
          "name": "dummy"
        }
      },
      "comment_count": 0,
      "state": "OPEN",
      "task_count": 0,
      "participants": [],
      "reason": "",
      "updated_on": "2021-02-15T18:39:37.654103+00:00",
      "author": {
        "display_name": "John Doe",
        "uuid": "{redacted}",
        "links": {
          "self": {
            "href": "https://api.bitbucket.org/2.0/users/redacted"
          },
          "html": {
            "href": "https://bitbucket.org/redacted/"
          },
          "avatar": {
            "href": "https://avatar-management--avatars.us-west-2.prod.public.atl-paas.net/initials/JD-0.png"
          }
        },
        "nickname": "john.doe",
        "type": "user",
        "account_id": "redacted"
      },
      "merge_commit": null,
      "closed_by": null
    }
    """

    val expected = PullRequestOut(
      html_url = uri"https://bitbucket.org/acme-corp/my-scala-steward/pull-requests/3",
      state = PullRequestState.Open,
      number = PullRequestNumber(3),
      title = "Dummy PR"
    )

    val actual = for {
      jsonValue <- parse(rawResponse)
      decoded <- json.pullRequestOutDecoder.decodeJson(jsonValue)
    } yield decoded

    assertEquals(actual, Right(expected))
  }
}
