package org.scalasteward.ghappfacade

import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val (facadeArgs, stewardArgs) = args.span(_ =!= "--")
    Cli.parseArgs(facadeArgs) match {
      case Cli.ParseResult.Success(config) =>
        Context.step0[IO](config).use(_.facadeAlg.runF(stewardArgs.drop(1)))
      case Cli.ParseResult.Help(help)   => Console[IO].println(help).as(ExitCode.Success)
      case Cli.ParseResult.Error(error) => Console[IO].errorln(error).as(ExitCode.Error)
    }
  }
}
