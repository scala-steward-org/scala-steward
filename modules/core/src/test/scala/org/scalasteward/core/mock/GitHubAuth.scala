package org.scalasteward.core.mock

import io.circe.syntax.EncoderOps
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.scalasteward.core.forge.github.{InstallationOut, RepositoriesOut, Repository, TokenOut}

object GitHubAuth extends Http4sDsl[MockEff] {
  def api(repositories: List[Repository]): HttpApp[MockEff] = HttpApp[MockEff] { req =>
    (req: @unchecked) match {
      case GET -> Root / "app" / "installations" =>
        Ok(List(InstallationOut(1L)).asJson.spaces2)
      case POST -> Root / "app" / "installations" / "1" / "access_tokens" =>
        Ok(TokenOut("some-token").asJson.spaces2)
      case GET -> Root / "installation" / "repositories" =>
        Ok(RepositoriesOut(repositories).asJson.spaces2)
    }
  }
}
