package org.scalasteward.core.application

import better.files.File
import cats.Monad
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.io.{FileAlg, FileData, WorkspaceAlg}
import org.scalasteward.core.sbt.{scalaStewardSbt, stewardPlugin}

final class PrepareEnvAlg[F[_]](implicit workspaceAlg: WorkspaceAlg[F],
                                fileAlg: FileAlg[F],
                                logger: Logger[F],
                                F: Monad[F]) {

  def prepareEnv: F[Unit] = {
    for {
      _ <- addGlobalPlugins
      _ <- workspaceAlg.cleanWorkspace
    } yield ()
  }

  def addGlobalPlugins: F[Unit] =
    for {
      _ <- logger.info("Add global sbt plugins")
      _ <- addGlobalPlugin(scalaStewardSbt)
      _ <- addGlobalPlugin(stewardPlugin)
    } yield ()

  def addGlobalPlugin(plugin: FileData): F[Unit] =
    List("0.13", "1.0").traverse_ { series =>
      sbtDir.flatMap(dir => fileAlg.writeFileData(dir / series / "plugins", plugin))
    }

  val sbtDir: F[File] =
    fileAlg.home.map(_ / ".sbt")

}
