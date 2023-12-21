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
import cats.effect.std.Console
import cats.syntax.all._
import java.nio.file.Files
import org.scalasteward.core.application.{Cli => CoreCli}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.io.FileAlg
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

final class FacadeAlg[F[_]](config: Config)(implicit
    console: Console[F],
    fileAlg: FileAlg[F],
    gitHubAppApiAlg: GitHubAppApiAlg[F],
    gitHubAuthAlg: GitHubAuthAlg[F],
    logger: Logger[F],
    F: Async[F]
) {
  private def createJWT: F[String] =
    gitHubAuthAlg.createJWT(config.gitHubApp, 2.minutes)

  private def stewardAllInstallations(stewardArgs: List[String]): F[Unit] =
    createJWT.flatMap { jwt =>
      gitHubAppApiAlg.installations(jwt).flatMap { installations =>
        installations.traverse_(installation => stewardInstallation(stewardArgs, installation))
      }
    }

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
      reposContent = repos.map(r => "- " + r.show).mkString("\n")
      reposFile = config.workspace / s"repos_${installation.id}.md"
      _ <- fileAlg.writeFile(reposFile, reposContent)
      askPassContent = s"""#!/bin/sh\necho ${token.token}"""
      askPassFile = config.workspace / s"ask-pass-${installation.id}.sh"
      _ <- fileAlg.writeFile(askPassFile, askPassContent)
      _ <- F.delay(askPassFile.toJava.setExecutable(true))
      args = s"--${CoreCli.name.workspace}" :: config.workspace.pathAsString ::
        s"--${CoreCli.name.reposFile}" :: reposFile.pathAsString ::
        s"--${CoreCli.name.gitAskPass}" :: askPassFile.pathAsString ::
        s"--${CoreCli.name.forgeType}" :: ForgeType.GitHub.asString ::
        s"--${CoreCli.name.doNotFork}" ::
        stewardArgs
      _ <- org.scalasteward.core.Main.runF[F](args)
    } yield ()

  private def repositoryToRepo(repository: Repository): F[Option[Repo]] =
    repository.full_name.split('/') match {
      case Array(owner, name) => F.pure(Some(Repo(owner, name)))
      case _                  => logger.error(s"Invalid repo $repository").as(None)
    }

  def runF(stewardArgs: List[String]): F[Unit] =
    stewardAllInstallations(stewardArgs)
}
