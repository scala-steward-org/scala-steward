package org.scalasteward

import cats.effect.{ExitCode, IO, IOApp}
import org.scalasteward.core.application.Context

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Context.create[IO](args).use(_.runF)

}
