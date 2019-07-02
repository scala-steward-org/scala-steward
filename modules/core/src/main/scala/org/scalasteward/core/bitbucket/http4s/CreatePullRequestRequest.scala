package org.scalasteward.core.bitbucket.http4s
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data.Repo
import io.circe.{Encoder, Json}

private[http4s] case class CreatePullRequestRequest(
    title: String,
    sourceBranch: Branch,
    sourceRepo: Repo,
    destinationBranch: Branch,
    description: String
)

private[http4s] object CreatePullRequestRequest {
  implicit val encoder: Encoder[CreatePullRequestRequest] = Encoder.instance { d =>
    Json.obj(
      ("title", Json.fromString(d.title)),
      (
        "source",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.sourceBranch.name)))),
          (
            "repository",
            Json.obj(("full_name", Json.fromString(s"${d.sourceRepo.owner}/${d.sourceRepo.repo}")))
          )
        )
      ),
      ("description", Json.fromString(d.description)),
      (
        "destination",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.destinationBranch.name))))
        )
      )
    )
  }
}
