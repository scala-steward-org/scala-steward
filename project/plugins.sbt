addSbtPlugin("com.codecommit" % "sbt-github-actions" % "0.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("com.github.tkawachi" % "sbt-doctest" % "0.9.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.19")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")

// This is only here so that Scala Steward updates the version in sbt/package.scala too.
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.29")
