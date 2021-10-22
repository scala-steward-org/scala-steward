package org.scalasteward.core.edit.scalafix

import cats.effect.Concurrent
import cats.syntax.all._
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

final class ScalafixCli[F[_]](implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Concurrent[F]
) {
  def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      projectDir = buildRootDir / "project"
      files <- (
        fileAlg.walk(buildRootDir).filter(_.extension.contains(".sbt")) ++
          fileAlg.walk(projectDir).filter(_.extension.contains(".scala"))
      ).map(_.pathAsString).compile.toList
      rules = migration.rewriteRules.map("--rules=" + _)
      _ <- processAlg.exec(scalafixBinary :: rules ++ files, buildRootDir)
    } yield ()

  def version: F[String] =
    workspaceAlg.rootDir
      .flatMap(processAlg.exec(Nel.of(scalafixBinary, "--version"), _))
      .map(_.mkString.trim)
}

object ScalafixCli {
  val scalafixBinary = "scalafix"
}
