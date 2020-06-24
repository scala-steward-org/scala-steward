package org.scalasteward.mill

package object plugin {
  implicit class AggOps[A](agg: mill.api.Loose.Agg[A]) {
    def iterator = agg.toSeq.iterator
  }
}