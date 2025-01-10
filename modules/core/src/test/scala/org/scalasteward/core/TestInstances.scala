package org.scalasteward.core

import cats.effect.IO
import eu.timepit.refined.scalacheck.numeric.*
import eu.timepit.refined.types.numeric.NonNegInt
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.*
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.*
import org.scalasteward.core.repoconfig.PullRequestFrequency.{Asap, Timespan}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.FiniteDuration

object TestInstances {
  val dummySha1: Sha1 =
    Sha1.unsafeFrom("da39a3ee5e6b4b0d3255bfef95601890afd80709")

  val dummyRepoCache: RepoCache =
    RepoCache(dummySha1, List.empty, Option.empty, Option.empty)

  val dummyRepoCacheWithParsingError: RepoCache =
    dummyRepoCache.copy(maybeRepoConfigParsingError = Some("Failed to parse .scala-steward.conf"))

  implicit val ioLogger: Logger[IO] =
    Slf4jLogger.getLogger[IO]

  implicit def scopeArbitrary[T](implicit arbT: Arbitrary[T]): Arbitrary[Scope[T]] =
    Arbitrary(
      arbT.arbitrary.flatMap { t =>
        Gen.oneOf(Scope(t, List.empty), Scope(t, List(Resolver.mavenCentral)))
      }
    )

  implicit def scopeCogen[T](implicit cogenT: Cogen[T]): Cogen[Scope[T]] =
    cogenT.contramap(_.value)

  implicit val updateArbitrary: Arbitrary[Update.Single] =
    Arbitrary(
      for {
        groupId <- Gen.alphaStr
        artifactId <- Gen.alphaStr
        currentVersion <- Gen.alphaStr
        newerVersion <- Gen.alphaStr
      } yield (groupId.g % artifactId.a % currentVersion %> newerVersion).single
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
    Cogen(
      _.alnumComponents
        .map {
          case n: Version.Component.Numeric => n.toBigInt.toLong
          case a: Version.Component.Alpha   => a.order.toLong
          case _                            => 0L
        }
        .sum
    )

  // repoconfig instances

  implicit val commitsConfigArbitrary: Arbitrary[CommitsConfig] =
    Arbitrary(for {
      message <- Arbitrary.arbitrary[Option[String]]
    } yield CommitsConfig(message))

  implicit val pullRequestFrequencyArbitrary: Arbitrary[PullRequestFrequency] =
    Arbitrary(Arbitrary.arbitrary[FiniteDuration].flatMap(fd => Gen.oneOf(Asap, Timespan(fd))))

  implicit val groupRepoConfigArbitrary: Arbitrary[GroupRepoConfig] =
    Arbitrary(for {
      pullRequestsConfig <- Arbitrary.arbitrary[PullRequestsConfig]
      pattern <- Arbitrary.arbitrary[UpdatePattern]
    } yield GroupRepoConfig(pullRequestsConfig, pattern))

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
        .map(_.map(prefix => VersionPattern(prefix = Some(prefix))))
    } yield UpdatePattern(groupId = groupId, artifactId = artifactId, version = version))

  private def smallListOf[A](maxSize: Int, genA: Gen[A]): Gen[List[A]] =
    Gen.choose(0, maxSize).flatMap(n => Gen.listOfN(n, genA))

  implicit val updatesConfigArbitrary: Arbitrary[UpdatesConfig] =
    Arbitrary(
      for {
        pin <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        allow <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        ignore <- smallListOf(4, Arbitrary.arbitrary[UpdatePattern])
        limit <- Arbitrary.arbitrary[Option[NonNegInt]]
        fileExtensions <- Arbitrary.arbitrary[Option[List[String]]]
      } yield UpdatesConfig(
        pin = Some(pin),
        allow = Some(allow),
        ignore = Some(ignore),
        limit = limit,
        fileExtensions = fileExtensions
      )
    )

  implicit val repoConfigArbitrary: Arbitrary[RepoConfig] =
    Arbitrary(
      for {
        commits <- Arbitrary.arbitrary[Option[CommitsConfig]]
        pullRequests <- Arbitrary.arbitrary[Option[PullRequestsConfig]]
        scalafmt <- Arbitrary.arbitrary[Option[ScalafmtConfig]]
        updates <- Arbitrary.arbitrary[Option[UpdatesConfig]]
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
