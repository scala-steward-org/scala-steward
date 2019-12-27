package org.scalasteward.core.data

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.edit.UpdateHeuristicTest.UpdateOps
import org.scalasteward.core.util.Nel
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExampleTest extends AnyWordSpec with Matchers {
  "Good examples of dependency definitions".which {
    "will be identified by scala-steward without any problems are".that {
      val goodExample1 = """val scalajsJqueryVersion = "0.9.3""""
      s"$goodExample1" in {
        val expectedResult = Some("""val scalajsJqueryVersion = "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(goodExample1)
          ._1 shouldBe expectedResult
      }

      val goodExample2 = """val SCALAJSJQUERYVERSION = "0.9.3""""
      s"$goodExample2" in {
        val expectedResult = Some("""val SCALAJSJQUERYVERSION = "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(goodExample2)
          ._1 shouldBe expectedResult
      }

      val goodExample3 = """val scalajsjquery = "0.9.3""""
      s"$goodExample3" in {
        val expectedResult = Some("""val scalajsjquery = "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(goodExample3)
          ._1 shouldBe expectedResult
      }

      val goodExample4 = """addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")"""
      s"$goodExample4" in {
        val expectedResult = Some("""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")""")
        Single("org.scala-js" % "sbt-scalajs" % "0.6.24", Nel.of("0.6.25"))
          .replaceVersionIn(goodExample4)
          ._1 shouldBe expectedResult
      }

      val goodExample5 = """"be.doeraene" %% "scalajs-jquery"  % "0.9.3""""
      s"$goodExample5" in {
        val expectedResult = Some(""""be.doeraene" %% "scalajs-jquery"  % "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(goodExample5)
          ._1 shouldBe expectedResult
      }

      val goodExample6 = """val `scalajs-jquery-version` = "0.9.3""""
      s"$goodExample6" in {
        val expectedResult = Some("""val `scalajs-jquery-version` = "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(goodExample6)
          ._1 shouldBe expectedResult
      }
    }
  }

  "Bad examples of dependency definitions".which {
    "won't be identified by scala-steward are".that {
      val badExample1 =
        """val scalajsJqueryVersion =
          |  "0.9.3"""".stripMargin
      s"$badExample1" in {
        val expectedResult = None
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(badExample1)
          ._1 shouldBe expectedResult
      }

      val badExample2 =
        """val scalajsJqueryVersion = "0.9.3" // val scalajsJqueryVersion = "0.9.3""""
      s"$badExample2" in {
        val expectedResult =
          Some("""val scalajsJqueryVersion = "0.9.3" // val scalajsJqueryVersion = "0.9.4"""")
        Single("be.doeraene" % "scalajs-jquery" % "0.9.3", Nel.of("0.9.4"))
          .replaceVersionIn(badExample2)
          ._1 shouldBe expectedResult
      }
    }
  }
}
