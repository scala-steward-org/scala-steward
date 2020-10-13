package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.SemigroupTests
import org.scalacheck.{Arbitrary, Gen}
import org.scalasteward.core.repoconfig.PullRequestFrequency.{Asap, Timespan}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import scala.concurrent.duration._

class PullRequestsConfigTest
    extends AnyFunSuite
    with Matchers
    with FunSuiteDiscipline
    with Configuration {

  implicit val pullRequestFrequencyArbitrary: Arbitrary[PullRequestFrequency] = Arbitrary(
    Gen.oneOf(Asap, Timespan(1.day), Timespan(7.days), Timespan(30.days))
  )
  implicit val pullRequestsConfigArbitrary: Arbitrary[PullRequestsConfig] = Arbitrary(for {
    e <- Arbitrary.arbitrary[Option[PullRequestFrequency]]
  } yield PullRequestsConfig(e))

  checkAll("Semigroup[PullRequestsConfig]", SemigroupTests[PullRequestsConfig].semigroup)
}
