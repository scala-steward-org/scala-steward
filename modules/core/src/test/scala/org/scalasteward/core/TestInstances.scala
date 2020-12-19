package org.scalasteward.core

import _root_.io.chrisdavenport.log4cats.Logger
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.{ContextShift, IO, Timer}
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.{Resolver, Scope, Update, Version}
import org.scalasteward.core.util.Change.{Changed, Unchanged}
import org.scalasteward.core.util.{Change, Nel}
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

  implicit def scopeArbitrary[T](implicit arbT: Arbitrary[T]): Arbitrary[Scope[T]] =
    Arbitrary(
      arbT.arbitrary.flatMap { t =>
        Gen.oneOf(Scope(t, List.empty), Scope(t, List(Resolver.mavenCentral)))
      }
    )

  implicit def scopeCogen[T](implicit cogenT: Cogen[T]): Cogen[Scope[T]] =
    cogenT.contramap(_.value)

  implicit val updateArbitrary: Arbitrary[Update] =
    Arbitrary(
      for {
        groupId <- Gen.alphaStr
        artifactId <- Gen.alphaStr
        currentVersion <- Gen.alphaStr
        newerVersion <- Gen.alphaStr
      } yield Single(groupId % artifactId % currentVersion, Nel.one(newerVersion))
    )

  private val hashGen: Gen[String] =
    for {
      sep <- Gen.oneOf('-', '+')
      maybeG <- Gen.option(Gen.const('g'))
      length <- Gen.choose(6, 8)
      rest <- Gen.listOfN(length, Gen.hexChar)
    } yield sep.toString + maybeG.getOrElse("") + rest.mkString

  implicit val versionArbitrary: Arbitrary[Version] = {
    val commonStrings =
      Gen.oneOf(
        "SNAP",
        "SNAPSHOT",
        "ALPHA",
        "PREVIEW",
        "BETA",
        "B",
        "M",
        "MILESTONE",
        "AM",
        "RC",
        "build",
        "final"
      )
    val versionComponent = Gen.frequency(
      (8, Gen.numChar.map(_.toString)),
      (5, Gen.const('.').map(_.toString)),
      (3, Gen.alphaChar.map(_.toString)),
      (2, Gen.const('-').map(_.toString)),
      (1, Gen.const('+').map(_.toString)),
      (1, commonStrings),
      (1, hashGen)
    )
    Arbitrary(Gen.listOf(versionComponent).map(_.mkString).map(Version.apply))
  }

  implicit val versionCogen: Cogen[Version] =
    Cogen(_.alnumComponents.map {
      case n: Version.Component.Numeric => n.toBigInt.toLong
      case a: Version.Component.Alpha   => a.order.toLong
      case _                            => 0L
    }.sum)
}
