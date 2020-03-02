package org.scalasteward

import better.files.File

package object docs {
  val out: File = File(buildinfo.BuildInfo.docsOut.getAbsolutePath)
}
