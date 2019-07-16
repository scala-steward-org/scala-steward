package org.scalasteward.core

import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.data.Version
import org.scalasteward.core.util.Change
import org.scalasteward.core.util.Change.{Changed, Unchanged}

object TestInstances {
  implicit def changeArbitrary[T](implicit arbT: Arbitrary[T]): Arbitrary[Change[T]] =
    Arbitrary(arbT.arbitrary.flatMap(t => Gen.oneOf(Changed(t), Unchanged(t))))

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
    Cogen(_.numericComponents.map(_.toLong).sum)
}
