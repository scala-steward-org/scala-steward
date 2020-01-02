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
import cats.implicits._
import org.scalasteward.core.application.Config
import org.scalasteward.core.build.system.BuildSystemAlg
import org.scalasteward.core.data.{ArtifactId, CrossDependency, Dependency, GroupId, Update}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import atto.Parser
import atto.Atto._
import scala.util.Try

object MavenAlg {

  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): BuildSystemAlg[F] =
    new BuildSystemAlg[F] {

      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          cmd = mvnCmd(command.Clean, command.mvnDepList)
          lines <- exec(cmd, repoDir)
          dependencies = parseDependencies(lines)
          //TODO: revisit scala check
          //          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
        } yield dependencies.distinct

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts") {
          fa
        }

      override def getUpdatesForRepo(repo: Repo): F[List[Update.Single]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          minorUpdatesRawLines <- exec(mvnCmd(command.MvnMinorUpdates), repoDir)
          minorUpdates = parseUpdates(minorUpdatesRawLines)
          majorUpdatesRawLines <- exec(mvnCmd(command.MvnDepUpdates), repoDir)
          majorUpdates = parseUpdates(majorUpdatesRawLines)
          pluginUpdatesRawLines <- exec(mvnCmd(command.PluginUpdates), repoDir)
          pluginUpdates = parseUpdates(pluginUpdatesRawLines)
          updates = Update.groupByArtifactIdName(minorUpdates ++ majorUpdates)
          result = updates ++ pluginUpdates
        } yield result

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        F.unit //fixme:implement
    }

  private def mvnCmd(commands: String*): Nel[String] =
    Nel.of("mvn", commands.flatMap(_.split(" ")): _*)

  def removeNoise(s: String): String = s.replace("[INFO]", "").trim

  def parseDependencies(lines: List[String]): List[Dependency] = {
    val pattern = """(.*):(.*):jar:(.*):compile""".r
    lines
      .map(removeNoise)
      .map(_.trim)
      .map { s =>
        Try {
          val pattern(groupId, artifactIdCross, currentVersion) = s
          val artifactId = artifactIdCross.split("_") //fixme: check if it's scala binary
          new Dependency(
            GroupId(groupId),
            ArtifactId(artifactId(0), artifactIdCross),
            currentVersion
          )
        }.toOption // TODO: this doesn't catch exceptions thrown by Try, if any
      // TODO: does regex throw exceptions if there are no matches?
      // todo: add logger and log the exceptions
      }
      .collect { case Some(x) => x }
  }

  def parseUpdates(lines: List[String]): List[Update.Single] =
    lines
      .map(removeNoise)
      .map(_.trim)
      .map { s =>
        MavenParser.parse(s).toOption
      }
      .collect { case Some(x) => x }
}

object MavenParser {

  private val dot: Parser[Char] = char('.')
  private val arrow = char('-') ~ char('>')

  case class Version(major: Int, minor: Int, patch: Option[Int]) {
    override def toString: String = {
      val x = (major :: minor :: Nil) ++ patch.toList
      x.map(_.toString).mkString(".")
    }
  }

  private val version: Parser[Version] = {
    val version3args: Parser[Version] =
      for {
        a <- int <~ dot
        b <- int <~ dot
        c <- int
      } yield Version(a, b, Some(c))

    val version2args: Parser[Version] =
      for {
        a <- int <~ dot
        b <- int <~ dot
        c <- int
      } yield Version(a, b, Some(c))

    version2args | version3args
  }

  val group: Parser[GroupId] = for {
    groupParts <- (many1(noneOf(".:")) ~ opt(dot)).many1
  } yield {
    GroupId(groupParts.toList.map { case (g, d) => (g.toList ++ d.toList).mkString }.mkString)
  }

  private def artifact: Parser[ArtifactId] = {
    val artifactString: Parser[String] = many1(noneOf("_ ")).map(_.toList.mkString)
    val underscore = char('_')

    for {
      init <- artifactString
      restOpt <- opt(many1(underscore ~ artifactString))
    } yield {
      restOpt.fold(ArtifactId(init, Option.empty[String])) { rest =>
        val crossVersion: Option[String] = for {
          possibleVersion <- rest.toList.lastOption.map {
            case (_, possibleVersion) => possibleVersion
          }
          crossVersion <- Option.when(possibleVersion.toFloatOption.isDefined)(possibleVersion)
        } yield crossVersion

        val suffix = crossVersion.fold(
          rest.map { case (underscore, str) => s"$underscore$str" }.toList.mkString
        ) { _ =>
          rest.init.map { case (underscore, str) => s"$underscore$str" }.mkString
        }

        ArtifactId(init + suffix, crossVersion)
      }
    }
  }

  val parser: Parser[Update.Single] = for {
    groupId <- group <~ char(':')
    artifactId <- artifact <~ many1(oneOf(" ."))
    currentVersion <- version
    _ <- opt(whitespace) ~ arrow ~ opt(whitespace)
    to <- version
  } yield {

    val dependency = Dependency(
      groupId = groupId,
      artifactId = artifactId,
      version = currentVersion.toString
    )

    Update.Single(
      crossDependency = CrossDependency(dependency),
      newerVersions = Nel.one[String](to.toString)
    )
  }

  def parse(raw: String): Either[String, Update.Single] =
    parser.parse(raw).done.either

}
