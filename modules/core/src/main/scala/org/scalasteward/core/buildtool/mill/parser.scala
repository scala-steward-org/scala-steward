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

package org.scalasteward.core.buildtool.mill

import cats.parse.Parser
import cats.parse.Rfc5234.sp
import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure}
import org.scalasteward.core.data.*
import scala.util.Try

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

  def parseMillVersion(s: String): Option[Version] =
    Option(s.trim).filter(_.nonEmpty).map(Version.apply)

  /** Used to correctly format the Mill plugin artifacts will when included look like:
    *   - import $ivy.`com.goyeau::mill-scalafix::0.2.10`
    *
    * However for the actual artifact if the user is on 0.10.x will look like:
    *   - mill-scalafix_mill0.10_.2.13
    *
    * @param artifactName
    *   name of the artifact parsed from the build file
    * @param millVersion
    *   the current Mill version being used
    * @return
    *   the newly put together ArtifactId
    */
  private def millPluginArtifact(artifactName: String, millVersion: Version): ArtifactId = {
    def format(major: String, minor: String) = s"${major}.${minor}_2.13"
    val millSuffix = millVersion.value.split('.') match {
      // Basically for right now we only accept "0.9.12", which is when this syntax for Mill plugins
      // was introduced, and all other pre v1 versions. Once it's for sure verified that v1 will also
      // follow this pattern we can include that.
      case Array(major, minor, patch) if major == "0" && minor == "9" && patch == "12" =>
        format(major, minor)
      case Array(major, minor, _) if major == "0" && Try(minor.toInt).map(_ > 9).getOrElse(false) =>
        format(major, minor)
      case _ => ""
    }

    ArtifactId(artifactName, Some(s"${artifactName}_mill${millSuffix}"))
  }

  def parseMillPluginDeps(s: String, millVersion: Version): List[Dependency] = {

    val importParser = Parser.string("import")
    val ivyParser = Parser.string("$ivy")
    val backtickParser = Parser.char('`')
    val doubleColonParser = Parser.string("::")
    val dotParser = Parser.char('.')
    val grabUntilColonParser = Parser.until(doubleColonParser)
    val grabntilBacktickParser = Parser.until(backtickParser)

    val parser =
      (importParser ~
        sp.rep ~
        ivyParser ~
        dotParser ~
        backtickParser) *>
        (grabUntilColonParser.string <*
          doubleColonParser) ~
        grabUntilColonParser.string ~
        (doubleColonParser *>
          grabntilBacktickParser.string <*
          backtickParser)

    val pluginDependencies = s
      .split("\n")
      .toList
      .map { line =>
        parser.parse(line)
      }
      .collect { case Right((_, ((org, artifact), version))) =>
        Dependency(GroupId(org), millPluginArtifact(artifact, millVersion), Version(version))
      }

    pluginDependencies
  }
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
          headers <- c.downField("headers").as[Option[List[Resolver.Header]]]
        } yield Resolver.MavenRepository(url, url, creds, headers)
      case "ivy" =>
        for {
          url <- c.downField("pattern").as[String]
          creds <- c.downField("auth").as[Option[Resolver.Credentials]]
          headers <- c.downField("headers").as[Option[List[Resolver.Header]]]
        } yield Resolver.IvyRepository(url, url, creds, headers)
      case typ => Left(DecodingFailure(s"Not a matching resolver type, $typ", c.history))
    }
  }

  implicit val decoder: Decoder[MillModule] = Decoder.instance(c =>
    for {
      name <- c.downField("name").as[String]
      resolvers <- c.downField("repositories").as(Decoder.decodeList(resolverDecoder))
      dependencies <- c.downField("dependencies").as[List[Dependency]]
    } yield MillModule(name, resolvers, dependencies)
  )
}
