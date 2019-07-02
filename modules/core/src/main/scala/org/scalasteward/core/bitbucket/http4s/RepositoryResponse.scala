package org.scalasteward.core.bitbucket.http4s
import org.scalasteward.core.git.Branch
import org.http4s.Uri
import org.scalasteward.core.vcs.data.Repo
import io.circe.Decoder
import cats.implicits._
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.UserOut
import io.circe.DecodingFailure

private[http4s] final case class RepositoryResponse(
    name: String,
    mainBranch: Branch,
    owner: UserOut,
    httpsCloneUrl: Uri,
    parent: Option[Repo]
)

private[http4s] object RepositoryResponse {

  implicit private val repoDecoder = Decoder.instance { c =>
    c.as[String].map(_.split('/')).flatMap { parts =>
      parts match {
        case Array(owner, name) => Repo(owner, name).asRight
        case _                  => DecodingFailure("Repo", c.history).asLeft
      }
    }
  }

  implicit val decoder: Decoder[RepositoryResponse] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <- c
        .downField("owner")
        .downField("username")
        .as[String]
        .orElse(c.downField("owner").downField("nickname").as[String])
      cloneUrl <- c
        .downField("links")
        .downField("clone")
        .downAt { p =>
          p.asObject
            .flatMap(o => o("name"))
            .flatMap(_.asString)
            .contains("https")
        }
        .downField("href")
        .as[Uri]
      defaultBranch <- c.downField("mainbranch").downField("name").as[Branch]
      maybeParent <- c.downField("parent").downField("full_name").as[Option[Repo]]
    } yield RepositoryResponse(name, defaultBranch, UserOut(owner), cloneUrl, maybeParent)
  }
}
