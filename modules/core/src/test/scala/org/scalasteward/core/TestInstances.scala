package org.scalasteward.core

import _root_.io.chrisdavenport.log4cats.Logger
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.{ContextShift, IO, Timer}
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.NonNegInt
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.{GroupId, Resolver, Scope, Update, Version}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.PullRequestFrequency.{Asap, Timespan}
import org.scalasteward.core.repoconfig._
import org.scalasteward.core.util.Change.{Changed, Unchanged}
import org.scalasteward.core.util.{Change, Nel}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object TestInstances {
  val dummyRepoCache: RepoCache =
    RepoCache(
      Sha1(HexString.unsafeFrom("da39a3ee5e6b4b0d3255bfef95601890afd80709")),
      List.empty,
      Option.empty
    )

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

  // repoconfig instances

  implicit val commitsConfigArbitrary: Arbitrary[CommitsConfig] =
    Arbitrary(for {
      message <- Arbitrary.arbitrary[Option[String]]
    } yield CommitsConfig(message))

  implicit val pullRequestFrequencyArbitrary: Arbitrary[PullRequestFrequency] =
    Arbitrary(Arbitrary.arbitrary[FiniteDuration].flatMap(fd => Gen.oneOf(Asap, Timespan(fd))))

  implicit val pullRequestsConfigArbitrary: Arbitrary[PullRequestsConfig] =
    Arbitrary(for {
      frequency <- Arbitrary.arbitrary[Option[PullRequestFrequency]]
    } yield PullRequestsConfig(frequency))

  implicit val pullRequestUpdateStrategyArbitrary: Arbitrary[PullRequestUpdateStrategy] =
    Arbitrary(
      Gen.oneOf(
        PullRequestUpdateStrategy.Always,
        PullRequestUpdateStrategy.Never,
        PullRequestUpdateStrategy.OnConflicts
      )
    )

  implicit val scalafmtConfigArbitrary: Arbitrary[ScalafmtConfig] =
    Arbitrary(for {
      runAfterUpgrading <- Arbitrary.arbitrary[Option[Boolean]]
    } yield ScalafmtConfig(runAfterUpgrading))

  implicit val updatePatternArbitrary: Arbitrary[UpdatePattern] =
    Arbitrary(for {
      groupId <- Arbitrary.arbitrary[String].map(GroupId.apply)
      artifactId <- Arbitrary.arbitrary[Option[String]]
      version <- Arbitrary
        .arbitrary[Option[String]]
        .map(_.map(suffix => UpdatePattern.Version(Some(suffix), None)))
    } yield UpdatePattern(groupId = groupId, artifactId = artifactId, version = version))

  private def smallListOf[A](maxSize: Int, genA: Gen[A]): Gen[List[A]] =
    Gen.choose(0, maxSize).flatMap(n => Gen.listOfN(n, genA))

  implicit val includeScalaStrategyArbitrary: Arbitrary[IncludeScalaStrategy] =
    Arbitrary(
      Gen.oneOf(
        IncludeScalaStrategy.Yes,
        IncludeScalaStrategy.Draft,
        IncludeScalaStrategy.No
      )
    )

  implicit val updatesConfigArbitrary: Arbitrary[UpdatesConfig] =
    Arbitrary(
      for {
        pin <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        allow <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        ignore <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        limit <- Arbitrary.arbitrary[Option[NonNegInt]]
        includeScala <- Arbitrary.arbitrary[Option[IncludeScalaStrategy]]
        fileExtensions <- Arbitrary.arbitrary[Option[List[String]]]
      } yield UpdatesConfig(
        pin = pin,
        allow = allow,
        ignore = ignore,
        limit = limit,
        includeScala = includeScala,
        fileExtensions = fileExtensions
      )
    )

  implicit val repoConfigArbitrary: Arbitrary[RepoConfig] =
    Arbitrary(
      for {
        commits <- Arbitrary.arbitrary[CommitsConfig]
        pullRequests <- Arbitrary.arbitrary[PullRequestsConfig]
        scalafmt <- Arbitrary.arbitrary[ScalafmtConfig]
        updates <- Arbitrary.arbitrary[UpdatesConfig]
        updatePullRequests <- Arbitrary.arbitrary[Option[PullRequestUpdateStrategy]]
      } yield RepoConfig(
        commits = commits,
        pullRequests = pullRequests,
        scalafmt = scalafmt,
        updates = updates,
        updatePullRequests = updatePullRequests
      )
    )
}
