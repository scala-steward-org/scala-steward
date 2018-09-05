package eu.timepit.scruffy

import java.io.IOException
import java.nio.file.{Files, Path}
import java.util.Comparator

import better.files.File
import cats.effect.IO
import cats.implicits._
import fs2.Stream

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

object io {
  def deleteRecursively(root: Path): IO[Unit] =
    IO {
      if (root.toFile.exists()) {
        // https://stackoverflow.com/a/35989142/460387
        Files
          .walk(root)
          .sorted(Comparator.reverseOrder())
          .forEach(path => { path.toFile.delete(); () })
      }
    }

  def exec(command: List[String], cwd: Path): IO[List[String]] =
    IO {
      val lb = ListBuffer.empty[String]
      val log = new ProcessLogger {
        override def out(s: => String): Unit = lb.append(s)
        override def err(s: => String): Unit = lb.append(s)
        override def buffer[T](f: => T): T = f
      }
      val exitCode = Process(command, cwd.toFile).!(log)
      if (exitCode != 0) throw new IOException(lb.mkString("\n"))
      lb.result()
    }

  def isSourceFile(path: Path): Boolean = {
    val name = path.toString
    (!name.contains(".git/")
    && (name.endsWith(".scala") || name.endsWith(".sbt"))
    && path.toFile.isFile)
  }

  def mkdirs(path: Path): IO[Unit] =
    IO(path.toFile.mkdirs()).void

  def updateDependency(repo: Repository, update: DependencyUpdate): IO[Unit] =
    walk(repo.dir).filter(isSourceFile).evalMap(updateDependency(_, update)).compile.drain

  def updateDependency(path: Path, update: DependencyUpdate): IO[Unit] =
    IO {
      val regex = s"${update.artifactId}.*?${update.currentVersion}".r
      val file = File(path)
      val oldContent = file.contentAsString
      var updated = false
      val newContent = regex.replaceAllIn(oldContent, m => {
        updated = true
        m.matched.replace(update.currentVersion, update.newerVersions.head)
      })
      if (updated) {
        file.write(newContent)
        ()
      }
    }

  def walk(root: Path): Stream[IO, Path] =
    Stream
      .eval(IO(Files.walk(root).iterator().asScala))
      .flatMap(Stream.fromIterator[IO, Path])
}
