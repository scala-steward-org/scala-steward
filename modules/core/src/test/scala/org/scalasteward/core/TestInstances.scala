package org.scalasteward.core

import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalasteward.core.model.Version

object TestInstances {
  implicit val arbitraryVersion: Arbitrary[Version] = {
    val versionChar = Gen.frequency(
      (8, Gen.numChar),
      (5, Gen.const('.')),
      (3, Gen.alphaChar),
      (2, Gen.const('-'))
    )
    Arbitrary(Gen.listOf(versionChar).map(_.mkString).map(Version.apply))
  }

  implicit val cogenVersion: Cogen[Version] =
    Cogen[String].contramap(_.value)
}
