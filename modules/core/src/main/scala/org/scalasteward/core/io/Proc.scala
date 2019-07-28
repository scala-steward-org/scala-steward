package org.scalasteward.core.io

import cats.effect.{Concurrent, ContextShift, Sync}
import cats.implicits._
import fs2.Stream
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Proc {
  def slurp[F[_]](
      cmd: List[String],
      timeout: FiniteDuration,
      out: String => F[Unit],
      err: String => F[Unit]
  )(implicit F: Concurrent[F], cs: ContextShift[F]): F[List[String]] =
    F.delay(new ProcessBuilder(cmd: _*).start()).flatMap { process =>
      val stdout = readInputStream[F](process.getInputStream, ExecutionContext.global).evalTap(out)
      val stderr = readInputStream[F](process.getErrorStream, ExecutionContext.global).evalTap(err)
      Stream(stdout, stderr).parJoinUnbounded.compile.toList

    // exitCode != 0, timeout, listbuffer
    }

  private def readInputStream[F[_]](is: InputStream, blockingExecutionContext: ExecutionContext)(
      implicit
      F: Sync[F],
      cs: ContextShift[F]
  ): Stream[F, String] =
    fs2.io
      .unsafeReadInputStream(F.pure(is), chunkSize = 4096, blockingExecutionContext)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
}
