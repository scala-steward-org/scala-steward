package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.SemigroupTests
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class CommitsConfigTest
    extends AnyFunSuite
    with Matchers
    with FunSuiteDiscipline
    with Configuration {
  implicit val commitsConfigArbitrary: Arbitrary[CommitsConfig] = Arbitrary(for {
    e <- Arbitrary.arbitrary[Option[String]]
  } yield CommitsConfig(e))

  checkAll("Semigroup[CommitsConfig]", SemigroupTests[CommitsConfig].semigroup)
}
