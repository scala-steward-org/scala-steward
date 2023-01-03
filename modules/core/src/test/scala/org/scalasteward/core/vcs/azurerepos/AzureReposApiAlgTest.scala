package org.scalasteward.core.vcs.azurerepos

import munit.CatsEffectSuite
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{BasicCredentials, HttpApp, Request, Uri}
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.AzureReposConfig
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.{VCSSelection, VCSType}

class AzureReposApiAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val user = AuthenticatedUser("user", "pass")
  private val repo = Repo("scala-steward-org", "scala-steward")
  private val apiHost = uri"https://dev.azure.com"

  private def hasAuthHeader(req: Request[MockEff], authorization: Authorization): Boolean =
    req.headers.get[Authorization].contains(authorization)

  private val basicAuth = Authorization(BasicCredentials(user.login, user.accessToken))

  object branchNameMatcher extends QueryParamDecoderMatcher[String]("name")
  object sourceRefNameMatcher
      extends QueryParamDecoderMatcher[String]("searchCriteria.sourceRefName")
  object targetRefNameMatcher
      extends QueryParamDecoderMatcher[String]("searchCriteria.targetRefName")

  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case req @ GET -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo
        if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |    "id": "3846fbbd-71a0-402b-8352-6b1b9b2088aa",
           |    "name": "scala-steward",
           |    "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward",
           |    "project": {
           |        "id": "2a608025-b9aa-4918-a306-5aae0a8b7458",
           |        "name": "scala-steward-org"
           |    },
           |    "defaultBranch": "refs/heads/main",
           |    "size": 7786160,
           |    "remoteUrl": "https://steward-user@dev.azure.com/azure-org/scala-steward-org/_git/scala-steward",
           |    "isDisabled": false
           |}""".stripMargin)

    case req @ GET -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "stats/branches"
        :? branchNameMatcher("main") if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |    "commit": {
           |        "commitId": "f55c9900528e548511fbba6874c873d44c5d714c"                          
           |    },
           |    "name": "main",
           |    "aheadCount": 0,
           |    "behindCount": 0,
           |    "isBaseVersion": true
           |}""".stripMargin)

    case req @ POST -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "pullrequests"
        if hasAuthHeader(req, basicAuth) =>
      Created(
        """
          |{
          |  "repository": {
          |    "id": "3846fbbd-71a0-402b-8352-6b1b9b2088aa",
          |    "name": "scala-steward",
          |    "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward",
          |    "project": {
          |      "id": "a7573007-bbb3-4341-b726-0c4148a07853",
          |      "name": "scala-steward-org"
          |    },
          |    "remoteUrl": "https://steward-user@dev.azure.com/azure-org/scala-steward-org/_git/scala-steward"
          |  },
          |  "pullRequestId": 22,
          |  "status": "active",
          |  "creationDate": "2016-11-01T16:30:31.6655471Z",
          |  "title": "Update cats-effect to 3.3.14",
          |  "description": "Updates org.typelevel:cats-effect  from 3.3.13 to 3.3.14.",
          |  "sourceRefName": "refs/heads/update/cats-effect-3.3.14",
          |  "targetRefName": "refs/heads/main",
          |  "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/22",
          |  "supportsIterations": true
          |}""".stripMargin
      )

    case req @ GET -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "pullrequests" :?
        sourceRefNameMatcher("refs/heads/update/cats-effect-3.3.14")
        +& targetRefNameMatcher("refs/heads/main") if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |   "value":[
           |      {
           |         "pullRequestId":26,
           |         "status":"active",
           |         "creationDate":"2022-07-24T16:58:51.0719239Z",
           |         "title":"Update cats-effect to 3.3.14",
           |         "description":"Updates [org.typelevel:cats-effect]",
           |         "sourceRefName":"refs/heads/update/cats-effect-3.3.14",
           |         "targetRefName":"refs/heads/main",
           |         "mergeStatus":"succeeded",
           |         "isDraft":false,
           |         "mergeId":"3ff8afa0-1147-4158-b215-74a0b5a2e162",
           |         "reviewers":[
           |            
           |         ],
           |         "url":"https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/26",
           |         "supportsIterations":true
           |      }
           |   ],
           |   "count":1
           |}""".stripMargin)

    case req @ PATCH -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "pullrequests" / "26"
        if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |  "repository": {
           |    "id": "3846fbbd-71a0-402b-8352-6b1b9b2088aa",
           |    "name": "scala-steward",
           |    "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward",
           |    "project": {
           |      "id": "a7573007-bbb3-4341-b726-0c4148a07853",
           |      "name": "scala-steward-org"
           |    },
           |    "remoteUrl": "https://steward-user@dev.azure.com/azure-org/scala-steward-org/_git/scala-steward"
           |  },
           |  "pullRequestId": 26,
           |  "status": "abandoned",
           |  "creationDate": "2016-11-01T16:30:31.6655471Z",
           |  "title": "Update cats-effect to 3.3.14",
           |  "description": "Updates org.typelevel:cats-effect  from 3.3.13 to 3.3.14.",
           |  "sourceRefName": "refs/heads/update/cats-effect-3.3.14",
           |  "targetRefName": "refs/heads/main",
           |  "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/26",
           |  "supportsIterations": true
           |}""".stripMargin)

    case req @ POST -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "pullrequests" / "26" / "threads"
        if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |   "id":17,
           |   "publishedDate":"2022-07-24T22:06:00.067Z",
           |   "lastUpdatedDate":"2022-07-24T22:06:00.067Z",
           |   "comments":[
           |      {
           |         "id":1,
           |         "parentCommentId":0,
           |         "content":"Test comment",
           |         "publishedDate":"2022-07-24T22:06:00.067Z",
           |         "lastUpdatedDate":"2022-07-24T22:06:00.067Z",
           |         "lastContentUpdatedDate":"2022-07-24T22:06:00.067Z"
           |      }
           |   ],
           |   "status":"active"
           |}""".stripMargin)

    case req @ POST -> Root / "azure-org" / repo.owner / "_apis/git/repositories" / repo.repo / "pullrequests" / "26" / "labels"
        if hasAuthHeader(req, basicAuth) =>
      Ok("""
           |{
           |    "id": "921dbff4-9c00-49d6-9262-ab0d6e4a13f1",
           |    "name": "dependency-updates",
           |    "active": true,
           |    "url": "https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/26/labels/921dbff4-9c00-49d6-9262-ab0d6e4a13f1"
           |}""".stripMargin)

    case _ => NotFound()
  })

  private val azureRepoCfg = AzureReposConfig(organization = Some("azure-org"))
  private val azureReposApiAlg = new VCSSelection[MockEff](
    config.copy(
      vcsCfg = config.vcsCfg.copy(apiHost = apiHost, tpe = VCSType.AzureRepos),
      azureReposConfig = azureRepoCfg
    ),
    user
  ).vcsApiAlg

  test("getRepo") {
    val obtained = azureReposApiAlg.getRepo(repo).runA(state)
    val expected = RepoOut(
      "scala-steward",
      UserOut("scala-steward-org"),
      None,
      Uri.unsafeFromString(
        "https://steward-user@dev.azure.com/azure-org/scala-steward-org/_git/scala-steward"
      ),
      Branch("refs/heads/main")
    )
    assertIO(obtained, expected)
  }

  test("getBranch") {
    val obtained = azureReposApiAlg.getBranch(repo, Branch("refs/heads/main")).runA(state)
    val expected = BranchOut(
      Branch("main"),
      CommitOut(Sha1(HexString.unsafeFrom("f55c9900528e548511fbba6874c873d44c5d714c")))
    )
    assertIO(obtained, expected)
  }

  test("createPullRequest") {
    val obtained = azureReposApiAlg
      .createPullRequest(
        repo,
        NewPullRequestData(
          title = "Update cats-effect to 3.3.14",
          body = "Updates org.typelevel:cats-effect  from 3.3.13 to 3.3.14.",
          head = "refs/heads/update/cats-effect-3.3.14",
          base = Branch("refs/heads/main"),
          labels = List.empty
        )
      )
      .runA(state)

    val expected = PullRequestOut(
      uri"https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/22",
      PullRequestState.Open,
      PullRequestNumber(22),
      "Update cats-effect to 3.3.14"
    )
    assertIO(obtained, expected)
  }

  test("listPullRequests") {
    val obtained = azureReposApiAlg
      .listPullRequests(
        repo,
        "refs/heads/update/cats-effect-3.3.14",
        Branch("refs/heads/main")
      )
      .runA(state)

    val expected = List(
      PullRequestOut(
        uri"https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/26",
        PullRequestState.Open,
        PullRequestNumber(26),
        "Update cats-effect to 3.3.14"
      )
    )
    assertIO(obtained, expected)
  }

  test("closePullRequest") {
    val obtained = azureReposApiAlg.closePullRequest(repo, PullRequestNumber(26)).runA(state)
    val expected = PullRequestOut(
      uri"https://dev.azure.com/azure-org/scala-steward-org/_apis/git/repositories/scala-steward/pullRequests/26",
      PullRequestState.Closed,
      PullRequestNumber(26),
      "Update cats-effect to 3.3.14"
    )

    assertIO(obtained, expected)
  }

  test("commentPullRequest") {
    val obtained = azureReposApiAlg
      .commentPullRequest(repo, PullRequestNumber(26), "Test comment")
      .runA(state)
    val expected = Comment("Test comment")

    assertIO(obtained, expected)
  }

  test("labelPullRequest") {
    azureReposApiAlg
      .labelPullRequest(repo, PullRequestNumber(26), List("dependency-updates"))
      .runA(state)
      .assert
  }
}
