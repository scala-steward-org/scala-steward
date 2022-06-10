/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.buildtool.gradle

import atto.Atto._
import atto._
import better.files.File
import java.nio.file.Paths
import cats.implicits._
import org.scalasteward.core.data._
import org.scalasteward.core.io.FileAlg
import cats.MonadError
import cats.Traverse
import cats.Applicative
import io.circe.Decoder
import cats.Defer

object parser {

  // private val dependency: Parser[Dependency] =
  //   for {
  //     _ <- opt(string("dependencies") ~ many(whitespace))
  //     groupId <- stringNoSpaceNoColon.map(GroupId.apply) <~ colon
  //     artifactId <- artifactId <~ colon <~ string("jar") <~ colon
  //     version <- stringNoSpaceNoColon <~ colon
  //     configurations <- configurations
  //   } yield Dependency(
  //     groupId = groupId,
  //     artifactId = artifactId,
  //     version = version,
  //     configurations = configurations
  //   )

  // def parseDependencies(input: List[String]): List[Dependency] =
  //   input.flatMap(line => dependency.parse(line).done.option)

  def parseDependencies[F[_]](
      lines: List[String]
  )(implicit
      fileAlg: FileAlg[F],
      F: MonadError[F, Throwable],
      A: Applicative[F],
      D: Defer[F]
  ): F[List[Scope.Dependencies]] =
    D.defer {

      def parseOutput: Parser[(List[Resolver], File)] =
        for {
          _ <- string("repositories") ~ many(whitespace)
          resolvers <- many(resolver)
          _ <- string("dependency-lock-file") ~ many(whitespace)
          lockFile <- dependencyLockFile
        } yield (resolvers, lockFile)

      val parseLines = F.catchNonFatal {
        fs2.Stream
          .emits(lines)
          .split(_.startsWith("> Task :"))
          .mapFilter { chunk =>
            parseOutput.parse(chunk.toList.mkString(System.lineSeparator)).done.option
          }
          .toList
      }

      parseLines.flatMap { items =>
        implicitly[Traverse[List]].sequence {
          items.map {
            case (resolvers, dependencyLockFile) =>
              fileAlg.readFile(dependencyLockFile).flatMap {
                case Some(contents) =>
                  A.map(parseDependencyLock(contents.trim)(F))(Scope(_, resolvers))
                case None =>
                  F.raiseError[Scope.Dependencies](
                    new RuntimeException(s"Couldn't read dependency lock file $dependencyLockFile")
                  )
              }
          }
        }
      }
    }

  private def parseDependencyLock[F[_]](
      input: String
  )(implicit
      F: MonadError[F, Throwable]
  ): F[List[Dependency]] =
    F.fromEither {
      io.circe.parser
        .decode[NebulaModules](input)
        .leftMap(CirceParseError("Failed to decode Modules", _): ParseError)
        .map { modules =>
          val skipConfigs = Set("nebulaRecommenderBom", "zinc", "resolutionRules")
          modules.configurations.view
            .filterKeys(k => !skipConfigs.contains(k))
            .values
            .flatMap { modules =>
              modules.flatMap { mod =>
                val Array(groupId, artifactId) =
                  mod.artifactName.split(":")
                mod.artifactInfo.lockedVersion match {
                  case None => Nil
                  case Some(lockedVersion) =>
                    List(
                      Dependency(
                        groupId = GroupId(groupId),
                        artifactId = ArtifactId(artifactId),
                        version = lockedVersion
                      )
                    )
                }
              }
            }
            .toList
        }
    }

  sealed trait ParseError extends RuntimeException {
    def message: String
    final override def getMessage: String = message
  }

  case class CirceParseError(message: String, cause: io.circe.Error) extends ParseError {
    final override def getCause: Throwable = cause
  }

  private val stringUntilEOL: Parser[String] =
    many1(noneOf(System.lineSeparator())).map(_.mkString_(""))

  private val dependencyLockFile: Parser[File] =
    for {
      _ <- many(whitespace)
      dependencyLockFile <- stringUntilEOL
    } yield Paths.get(dependencyLockFile)

  private val resolver: Parser[Resolver] = {
    for {
      _ <- string("name: ")
      name <- stringUntilEOL <~ string(System.lineSeparator)
      _ <- string("url: ")
      url <- stringUntilEOL <~ string(System.lineSeparator)
    } yield Resolver.MavenRepository(name, url, None)
  }

  case class NebulaModules(configurations: Map[String, List[NebulaModule]])
  object NebulaModules {
    implicit val decoder: Decoder[NebulaModules] =
      Decoder.decodeMap[String, List[NebulaModule]].map(NebulaModules(_))
  }

  case class NebulaModule(artifactName: String, artifactInfo: NebulaModuleInfo)
  object NebulaModule {
    implicit val decoder: Decoder[List[NebulaModule]] =
      Decoder
        .decodeMap[String, NebulaModuleInfo]
        .map(kvs => kvs.toList.map(kv => NebulaModule(kv._1, kv._2)))
  }

  // Models https://github.com/nebula-plugins/gradle-dependency-lock-plugin/blob/main/src/main/groovy/nebula/plugin/dependencylock/model/LockValue.groovy
  case class NebulaModuleInfo(
      lockedVersion: Option[String],
      transitiveDeps: Option[List[String]],
      firstLevelTransitive: Option[List[String]],
      requested: Option[String],
      viaOverride: Option[String],
      childrenVisited: Boolean,
      isProject: Boolean
  )

  object NebulaModuleInfo {
    implicit val decoder: Decoder[NebulaModuleInfo] = Decoder.instance(c =>
      for {
        lockedVersion <- c.downField("locked").as[Option[String]]
        transitiveDeps <- c.downField("transitive").as[Option[List[String]]]
        firstLevelTransitive <- c.downField("firstLevelTransitive").as[Option[List[String]]]
        requested <- c.downField("requested").as[Option[String]]
        viaOverride <- c.downField("viaOverride").as[Option[String]]
        childrenVisited <- c.downField("childrenVisited").as[Option[Boolean]]
        isProject <- c.downField("project").as[Option[Boolean]]
      } yield NebulaModuleInfo(
        lockedVersion,
        transitiveDeps,
        firstLevelTransitive,
        requested,
        viaOverride,
        childrenVisited.getOrElse(false),
        isProject.getOrElse(false)
      )
    )
  }
}
