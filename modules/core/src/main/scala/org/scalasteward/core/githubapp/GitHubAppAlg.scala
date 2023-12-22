package org.scalasteward.core.githubapp

import better.files.File
import cats.effect.{ExitCode, Sync}
import cats.effect.syntax.all._
import cats.syntax.all._
import java.nio.file.Paths
import org.scalasteward.core.application.{Config, StewardAlg}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.FileAlg
import org.typelevel.log4cats.Logger
import scala.concurrent.duration._

final class GitHubAppAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    gitHubAppApiAlg: GitHubAppApiAlg[F],
    gitHubAuthAlg: GitHubAuthAlg[F],
    stewardAlg: StewardAlg[F],
    F: Sync[F]
) {
  def runF: F[ExitCode] =
    stewardAllInstallations.as(ExitCode.Success)

  private def stewardAllInstallations: F[Unit] =
    createJWT.flatMap { jwt =>
      gitHubAppApiAlg.installations(jwt).flatMap { installations =>
        installations.traverse_(installation => stewardInstallation(installation))
      }
    }

  private def createJWT: F[String] =
    gitHubAuthAlg.createJWT(config.githubApp.get, 2.minutes)

  private def stewardInstallation(installation: InstallationOut): F[Unit] =
    for {
      jwt <- createJWT
      token <- gitHubAppApiAlg.accessToken(jwt, installation.id)
      repos <- gitHubAppApiAlg
        .repositories(token.token)
        .flatMap(_.repositories.traverseFilter(repositoryToRepo))
      reposContent = repos.map(r => "- " + r.show).mkString("\n")
      reposFile = File(Paths.get(java.net.URI.create(config.reposFiles.head.renderString)))
      _ <- fileAlg.writeFile(reposFile, reposContent)
      askPassContent = s"""#!/bin/sh\necho ${token.token}"""
      askPassFile = config.gitCfg.gitAskPass
      _ <- fileAlg.writeFile(askPassFile, askPassContent)
      _ <- F.delay(askPassFile.toJava.setExecutable(true))
      _ <- stewardAlg.runF /*.guarantee {
        List(reposFile, askPassFile).traverse_(fileAlg.deleteForce)
      }*/
    } yield ()

  private def repositoryToRepo(repository: Repository): F[Option[Repo]] =
    repository.full_name.split('/') match {
      case Array(owner, name) => F.pure(Some(Repo(owner, name)))
      case _                  => logger.error(s"Invalid repo $repository").as(None)
    }
}
