package org.scalasteward.docs

import better.files.Dsl.SymbolicOperations
import io.circe.Encoder
import io.circe.syntax._
import org.scalasteward.core.repoconfig.PullRequestFrequency._
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy.{Always, Never, OnConflicts}
import org.scalasteward.core.repoconfig.{
  PullRequestFrequency,
  PullRequestUpdateStrategy,
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
    s"""|```properties
        |$content
        |```""".stripMargin
  }

  val possibleValues = "*Possible values*"
  val default = "*Default*"
  val examples = "*Examples*"

  val pullRequests = "pullRequests"
  val frequency = "frequency"
  val asap: PullRequestFrequency = Asap
  val daily: PullRequestFrequency = Daily
  val weekly: PullRequestFrequency = Weekly
  val monthly: PullRequestFrequency = Monthly
  val updates = "updates"
  val limit = "limit"
  val updatesPullRequests = "updatesPullRequests"

  (out / "scala-steward.conf.md") < s"""
# ${RepoConfigAlg.repoConfigBasename}

## $pullRequests

### $frequency

Allows to control how often or when Scala Steward is allowed to create pull requests.

$possibleValues:
 * ${showCode(asap)}:
   Bla bla bla
   
 * ${showCode(daily)}:
   Bla bla bla
 
 * ${showCode(weekly)}:
 
 * ${showCode(monthly)}:
  
 * `"<CRON expression>"`:


$default: `${show(PullRequestFrequency.default)}`

$examples:

${validate(pullRequests + "." + frequency + " = " + show(weekly))}

## $updates

### $limit

$default: ${showCode(UpdatesConfig().limit)}

$examples:

${validate(updates + "." + limit + " = " + "5")}

## $updatesPullRequests

$possibleValues:
  * ${showCode(Always: PullRequestUpdateStrategy)}
  * ${showCode(Never: PullRequestUpdateStrategy)}
  * ${showCode(OnConflicts: PullRequestUpdateStrategy)}

$default: ${showCode(PullRequestUpdateStrategy.default)}

$examples:

""".trim + "\n"
}
