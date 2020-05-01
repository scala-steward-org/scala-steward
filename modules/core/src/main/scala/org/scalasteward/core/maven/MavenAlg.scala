/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.maven

import better.files.File
import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.build.system.BuildSystemAlg
import org.scalasteward.core.data._
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

object MavenAlg {

  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      logger: Logger[F],
      F: Monad[F]
  ): BuildSystemAlg[F] = new BuildSystemAlg[F] {

    import MavenParser._

    override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] = {
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        listDependenciesCommand = mvnCmd(command.Clean, command.mvnDepList)
        listResolversCommand = mvnCmd(command.Clean, command.ListRepositories)
        repositoriesRaw <- exec(listResolversCommand, repoDir) <* logger.info(
          s"running $listResolversCommand for $repo"
        )
        dependenciesRaw <- exec(listDependenciesCommand, repoDir) <* logger.info(
          s"running $listDependenciesCommand for $repo"
        )
        _ <- logger.info(dependenciesRaw.mkString("\n"))
        (_, dependencies) = parseAllDependencies(dependenciesRaw)
        (_, resolvers) = parseResolvers(repositoriesRaw.mkString("\n"))
      } yield {
        val deps = dependencies.distinct
        List(Scope(deps, resolvers))
      }

    }

    def exec(command: Nel[String], repoDir: File): F[List[String]] =
      maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

    def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
      if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

    def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
      fileAlg.removeTemporarily(dir / ".jvmopts") {
        fa
      }

    override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] = {
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        runScalafixCmd = mvnCmd(command.Clean, command.ScalafixMigrations)
        _ <- exec(runScalafixCmd, repoDir) <* logger.info(
          s"running $runScalafixCmd for $repo"
        )
      } yield ()
    }
  }

  private def mvnCmd(commands: String*): Nel[String] =
    Nel.of("mvn", commands.flatMap(_.split(" ")): _*)

}

object MavenParser {
  import atto._
  import Atto._
  import cats.implicits._

  private val dot: Parser[Char] = char('.')
  private val underscore = char('_')
  private val colon: Parser[Char] = char(':')

  private val group: Parser[GroupId] = (for {
    x <- (many1(noneOf(".:")) ~ opt(dot)).many1
    _ <- colon
  } yield {
    val parts: NonEmptyList[(NonEmptyList[Char], Option[Char])] = x
    val groupVal = x.toList.map { case (g, d) => (g.toList ++ d.toList).mkString }.mkString
    GroupId(groupVal)
  }).named("group")

  private val version3args: Parser[String] =
    for {
      a <- int <~ dot
      b <- int <~ dot
      c <- int
    } yield s"$a.$b.$c"

  private val artifact: Parser[ArtifactId] = {
    val artifactString: Parser[String] = many1(noneOf("_ :")).map(_.toList.mkString)

    for {
      init <- artifactString
      restOpt <- opt(many1(underscore ~ artifactString))
      _ <- colon
    } yield {
      restOpt.fold(ArtifactId(init, Option.empty[String])) { rest =>
        val crossVersion: Option[String] =
          Option.when(rest.last._2.toFloatOption.isDefined)(rest.last._2)

        val suffix = crossVersion.fold(
          rest.map { case (underscore, str) => s"${underscore}${str}" }.toList.mkString
        ) { _ =>
          rest.init.map { case (underscore, str) => s"${underscore}${str}" }.mkString
        }

        ArtifactId(init + suffix, maybeCrossName = crossVersion)
      }
    }
  }

  private val parserDependency = for {
    _ <- opt(string("[INFO]") <~ many(whitespace))
    g <- group
    a <- artifact
    _ <- string("jar") <~ colon
    v <- version3args <~ colon <~ string("compile")
  } yield {
    Dependency(groupId = g, artifactId = a, version = v)
  }

  def parseAllDependencies(
      input: List[String]
  ): (List[(String, String)], List[Dependency]) =
    input
      .map { line =>
        val either = parserDependency.parse(line).done.either
        either.leftMap(err => line -> err)
      }
      .partitionMap(identity)

  val resolverIdParser: Parser[String] = many1(noneOf(" ")).map(_.toList.mkString)
  val urlParser: Parser[String] = many1(noneOf(" ")).map(_.toList.mkString)

  private val parserResolver = for {
    _ <- many(whitespace)
    _ <- string("id:") ~ whitespace
    id <- resolverIdParser
    _ <- many(whitespace) <~ string("url:") <~ whitespace
    url <- resolverIdParser
    _ <- many(anyChar)
  } yield {
    Resolver.MavenRepository(id, url)
  }

  def parseResolvers(raw: String) = {
    raw
      .split("""\[INFO\]""")
      .toList
      .map(line => parserResolver.parse(line).done.either)
      .partitionMap(identity)
  }

}
