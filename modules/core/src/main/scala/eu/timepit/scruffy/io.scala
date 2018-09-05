package eu.timepit.scruffy

import java.io.IOException
import java.nio.file.{Files, Path}
import java.util.Comparator

import cats.effect.IO
import cats.implicits._

import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

object io {
  def deleteRecursively(rootPath: Path): IO[Unit] =
    IO {
      if (rootPath.toFile.exists()) {
        println(s"Removing $rootPath")
        // https://stackoverflow.com/a/35989142/460387
        Files
          .walk(rootPath)
          .sorted(Comparator.reverseOrder())
          .forEach(path => { path.toFile.delete(); () })
      }
    }

  def execLines(command: List[String], cwd: Path): IO[List[String]] =
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

  def mkdirs(path: Path): IO[Unit] =
    IO(path.toFile.mkdirs()).void
}
