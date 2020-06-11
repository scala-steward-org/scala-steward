package org.scalasteward.core.buildtool.mill

import cats.implicits._
import io.circe.{Decoder, DecodingFailure}
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Resolver}

object parser {
  sealed trait ParseError extends RuntimeException {
    def message: String

    final override def getMessage: String = message
  }
  case class CirceParseError(message: String, cause: io.circe.Error) extends ParseError {
    final override def getCause: Throwable = cause
  }

  def parseModules[F[_]](
      input: String
  ): Either[ParseError, List[MillModule]] =
    for {
      json <-
        io.circe.parser
          .decode[Modules](input)
          .leftMap(CirceParseError("Failed to decode Modules", _): ParseError)
    } yield json.modules

}

case class Modules(modules: List[MillModule])
object Modules {
  implicit val decoder: Decoder[Modules] = Decoder.forProduct1("modules")(apply)
}

case class MillModule(name: String, repositories: List[Resolver], dependencies: List[Dependency])

object MillModule {
  val resolverDecoder: Decoder[Resolver] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "maven" =>
        for {
          url <- c.downField("url").as[String]
          creds <- c.downField("auth").as[Option[Resolver.Credentials]]
        } yield Resolver.MavenRepository(url, url, creds)
      case "ivy" =>
        for {
          url <- c.downField("pattern").as[String]
          creds <- c.downField("auth").as[Option[Resolver.Credentials]]
        } yield Resolver.IvyRepository(url, url, creds)
      case typ => Left(DecodingFailure(s"Not a matching resolver type, $typ", c.history))
    }
  }

  val dependencyDecoder: Decoder[Dependency] = Decoder.instance { c =>
    for {
      groupId <- c.downField("groupId").as[GroupId]
      artifactId <- c.downField("artifactId").as[String].map(aid => ArtifactId(aid, None))
      version <- c.downField("version").as[String]
    } yield Dependency(groupId, artifactId, version)
  }

  implicit val decoder: Decoder[MillModule] = Decoder.instance(c =>
    for {
      name <- c.downField("name").as[String]
      resolvers <- c.downField("repositories").as(Decoder.decodeList(resolverDecoder))
      dependencies <- c.downField("dependencies").as(Decoder.decodeList(dependencyDecoder))
    } yield MillModule(name, resolvers, dependencies)
  )
}
