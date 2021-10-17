package org.scalasteward.core.mavencentral

import cats.Applicative
import cats.syntax.functor._
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalasteward.core.data.Dependency
import org.scalasteward.core.util.HttpJsonClient

/** See the section labelled 'REST API' in [[https://search.maven.org/classic/#api]]
  */
class MavenCentralApiAlg[F[_]: Applicative](implicit
  client: HttpJsonClient[F]
) {

  def searchForDocumentOn(dependency: Dependency): F[Option[Document]] = client.get[SearchOut](
    uri"https://search.maven.org/solrsearch/select".withQueryParam(
      "q",
      s"g:${dependency.groupId.value}+AND+a:${dependency.artifactId.name}+AND+v:${dependency.version}"),
    req => Applicative[F].point(req)
  ).map(_.response.docs.find(_.dependency == dependency))
}
