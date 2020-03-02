package org.scalasteward.docs

import better.files.Dsl.SymbolicOperations
import io.circe.Encoder
import io.circe.syntax._
import org.scalasteward.core.repoconfig.PullRequestFrequency._
import org.scalasteward.core.repoconfig.{
  PullRequestFrequency,
  RepoConfig,
  RepoConfigAlg,
  UpdatesConfig
}

object ScalaStewardConf extends App {
  def show[A: Encoder](a: A): String =
    a.asJson.noSpaces

  def showCode[A: Encoder](a: A): String =
    s"`${show(a)}`"

  def validate(content: String): String = {
    RepoConfigAlg.parseRepoConfig(content).toOption.get
    val md =
      s"""|```properties
          |${content.trim}
          |```""".stripMargin.trim
    md
  }

  val pullRequests = "pullRequests"
  val frequency = "frequency"
  val asap: PullRequestFrequency = Asap
  val daily: PullRequestFrequency = Daily
  val weekly: PullRequestFrequency = Weekly
  val monthly: PullRequestFrequency = Monthly
  val updates = "updates"
  val limit = "limit"

  (out / "scala-steward.conf.md") < s"""
# ${RepoConfigAlg.repoConfigBasename}

## $pullRequests

### $frequency

Allows to control how often or when Scala Steward is allowed to create pull requests.

Possible values:
 * ${showCode(asap)}:
   Bla bla bla
   
 * ${showCode(daily)}:
   Bla bla bla
 
 * ${showCode(weekly)}:
 
 * ${showCode(monthly)}:
  
 * `"<CRON expression>"`:


Default: `${show(PullRequestFrequency.default)}`

${validate(pullRequests + "." + frequency + " = " + show(weekly))}

## $updates

### $limit

Default: ${showCode(UpdatesConfig().limit)}

## updatesPullRequests

Possible values:

Default: ${showCode(RepoConfig().updatePullRequests)}

""".trim + "\n"
}
