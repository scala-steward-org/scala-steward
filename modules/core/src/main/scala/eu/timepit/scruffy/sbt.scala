package eu.timepit.scruffy

import java.nio.file.Path

import cats.effect.IO

object sbt {
  def dependencyUpdates(repoDir: Path): IO[List[DependencyUpdate]] =
    io.execLines(List("sbt", "-no-colors", "dependencyUpdates"), repoDir)
      .map(toDependencyUpdates)

  def pluginsUpdates(repoDir: Path): IO[List[DependencyUpdate]] =
    io.execLines(List("sbt", "-no-colors", ";reload plugins; dependencyUpdates"), repoDir)
      .map(toDependencyUpdates)

  def toDependencyUpdates(lines: List[String]): List[DependencyUpdate] =
    lines.flatMap { line =>
      DependencyUpdate.fromString(line.replace("[info]", "").trim).toSeq
    }
}
