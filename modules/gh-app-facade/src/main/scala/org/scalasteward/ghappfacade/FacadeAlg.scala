/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.ghappfacade

import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import org.scalasteward.core.application.{Cli => CoreCli}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

final class FacadeAlg[F[_]](config: FacadeConfig)(implicit
    fileAlg: FileAlg[F],
    gitHubAppApiAlg: GitHubAppApiAlg[F],
    gitHubAuthAlg: GitHubAuthAlg[F],
    logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Async[F]
) {
  def runF(stewardArgs: List[String]): F[Unit] =
    stewardAllInstallations(stewardArgs)

  private def stewardAllInstallations(stewardArgs: List[String]): F[Unit] =
    createJWT.flatMap { jwt =>
      gitHubAppApiAlg.installations(jwt).flatMap { installations =>
        installations.traverse_(installation => stewardInstallation(stewardArgs, installation))
      }
    }

  private def createJWT: F[String] =
    gitHubAuthAlg.createJWT(config.gitHubApp, 2.minutes)

  private def stewardInstallation(
      stewardArgs: List[String],
      installation: InstallationOut
  ): F[Unit] =
    for {
      jwt <- createJWT
      token <- gitHubAppApiAlg.accessToken(jwt, installation.id)
      repos <- gitHubAppApiAlg
        .repositories(token.token)
        .flatMap(_.repositories.traverseFilter(repositoryToRepo))
      workspace <- workspaceAlg.rootDir
      reposContent = repos.map(r => "- " + r.show).mkString("\n")
      reposFile = workspace / s"repos_${installation.id}.md"
      _ <- fileAlg.writeFile(reposFile, reposContent)
      askPassContent = s"""#!/bin/sh\necho ${token.token}"""
      askPassFile = workspace / s"ask-pass-${installation.id}.sh"
      _ <- fileAlg.writeFile(askPassFile, askPassContent)
      _ <- F.delay(askPassFile.toJava.setExecutable(true))
      args = List(
        toArg(CoreCli.name.workspace, workspace.pathAsString),
        toArg(CoreCli.name.reposFile, reposFile.pathAsString),
        toArg(CoreCli.name.gitAskPass, askPassFile.pathAsString),
        toArg(CoreCli.name.forgeType, ForgeType.GitHub.asString),
        toArg(CoreCli.name.doNotFork)
      ).flatten ++ stewardArgs
      _ <- org.scalasteward.core.Main.runF[F](args).guarantee {
        List(reposFile, askPassFile).traverse_(fileAlg.deleteForce)
      }
    } yield ()

  private def repositoryToRepo(repository: Repository): F[Option[Repo]] =
    repository.full_name.split('/') match {
      case Array(owner, name) => F.pure(Some(Repo(owner, name)))
      case _                  => logger.error(s"Invalid repo $repository").as(None)
    }

  private def toArg(name: String): List[String] =
    List(s"--$name")

  private def toArg(name: String, value: String): List[String] =
    toArg(name) :+ value
}
