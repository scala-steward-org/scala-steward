package org.scalasteward.core.repoconfig

import cats.kernel.laws.discipline.SemigroupTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary

class CommitsConfigTest extends DisciplineSuite {
  implicit val commitsConfigArbitrary: Arbitrary[CommitsConfig] = Arbitrary(for {
    e <- Arbitrary.arbitrary[Option[String]]
  } yield CommitsConfig(e))

  checkAll("Semigroup[CommitsConfig]", SemigroupTests[CommitsConfig].semigroup)
}
