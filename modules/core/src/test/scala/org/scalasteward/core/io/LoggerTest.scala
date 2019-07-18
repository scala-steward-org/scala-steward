package org.scalasteward.core.io

import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object LoggerTest {
  implicit val ioLogger: Logger[IO] = Slf4jLogger.getLogger[IO]
}
