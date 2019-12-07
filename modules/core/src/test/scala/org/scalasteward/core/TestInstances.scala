package org.scalasteward.core

import _root_.io.chrisdavenport.log4cats.Logger
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.{ContextShift, IO, Timer}
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.data.Version
import org.scalasteward.core.util.Change
import org.scalasteward.core.util.Change.{Changed, Unchanged}
import scala.concurrent.ExecutionContext

object TestInstances {
  implicit def changeArbitrary[T](implicit arbT: Arbitrary[T]): Arbitrary[Change[T]] =
    Arbitrary(arbT.arbitrary.flatMap(t => Gen.oneOf(Changed(t), Unchanged(t))))

  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit val ioLogger: Logger[IO] =
    Slf4jLogger.getLogger[IO]

  implicit val ioTimer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  implicit val versionArbitrary: Arbitrary[Version] = {
    val versionChar = Gen.frequency(
      (8, Gen.numChar),
      (5, Gen.const('.')),
      (3, Gen.alphaChar),
      (2, Gen.const('-'))
    )
    Arbitrary(Gen.listOf(versionChar).map(_.mkString).map(Version.apply))
  }

  implicit val versionCogen: Cogen[Version] =
    Cogen(_.alnumComponents.map {
      case Version.Component.Numeric(value) => BigInt(value).toLong
      case a @ Version.Component.Alpha(_)   => a.order.toLong
      case _                                => 0L
    }.sum)
}
