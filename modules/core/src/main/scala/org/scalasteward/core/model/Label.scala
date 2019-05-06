package org.scalasteward.core.model

sealed trait Label {
  def name: String
}

object Label {
  final case object LibraryUpdate extends Label {
    val name = "library-update"
  }

  final case object TestLibraryUpdate extends Label {
    val name = "test-library-update"
  }

  final case object SbtPluginUpdate extends Label {
    val name = "sbt-plugin-update"
  }
}
