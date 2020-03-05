package org.scalasteward.docs

import better.files.Dsl.SymbolicOperations
import io.circe.Encoder
import io.circe.syntax._
import org.scalasteward.core.repoconfig.PullRequestFrequency._
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy.{Always, Never, OnConflicts}
import org.scalasteward.core.repoconfig.{
  CommitsConfig,
  PullRequestFrequency,
  PullRequestUpdateStrategy,
  PullRequestsConfig,
  RepoConfig,
  RepoConfigAlg,
  UpdatesConfig
}

object ScalaStewardConf extends App {
  def show[A: Encoder](a: A): String =
    a.asJson.noSpaces

  def showCode[A: Encoder](a: A): String =
    s"`${show(a)}`"

  def verify(content: String)(expected: RepoConfig): String = {
    val parsed = RepoConfigAlg.parseRepoConfig(content).toOption
    require(
      parsed.contains(expected),
      s"""|
          |parsed:   $parsed
          |expected: ${Some(expected)}
          |""".stripMargin
    )
    s"""|```properties
        |$content
        |```""".stripMargin
  }

  val possibleValues = "*Possible values:*"
  val default = "*Default:*"
  val examples = "*Examples:*"

  val commits = "commits"
  val message = "message"
  val pullRequests = "pullRequests"
  val frequency = "frequency"
  val asap: PullRequestFrequency = Asap
  val daily: PullRequestFrequency = Daily
  val weekly: PullRequestFrequency = Weekly
  val monthly: PullRequestFrequency = Monthly
  val updates = "updates"
  val limit = "limit"
  val updatePullRequests = "updatePullRequests"

  (out / "scala-steward.conf.md") < s"""
# ${RepoConfigAlg.repoConfigBasename}

You can add `<YOUR_REPO>/.scala-steward.conf` to configure how Scala Steward updates your repository.

## $commits

### $commits.$message

If set, Scala Steward will use this message template for the commit messages and PR titles.
Supported variables: $${artifactName}, $${currentVersion}, $${nextVersion} and $${default}

$default "$${default}" which is equivalent to "Update $${artifactName} to $${nextVersion}" 

${
    val value = "Update ${artifactName} from ${currentVersion} to ${nextVersion}"
    verify(commits + "." + message + s""" = "$value"""")(
      RepoConfig(commits = CommitsConfig(message = Some(value)))
    )
  }

## $pullRequests

### $pullRequests.$frequency

Allows to control how often or when Scala Steward is allowed to create pull requests.

$possibleValues
 * ${showCode(asap)}

   PRs are created without delay.

 * ${showCode(daily)} | ${showCode(weekly)}  | ${showCode(monthly)}

   PRs are created at least 1 day | 7 days | 30 days after the last PR.

 * `"<CRON expression>"`

   PRs are created roughly according to the given CRON expression.

   CRON expressions consist of five fields:
   minutes, hour of day, day of month, month, and day of week.

   See https://www.alonsodomin.me/cron4s/userguide/index.html#parsing for
   more information about the CRON expressions that are supported.

   Note that the date parts of the CRON expression are matched exactly while the the time parts are only used to abide to the frequency of the given expression.


$default `${show(PullRequestFrequency.default)}`

$examples

${verify(pullRequests + "." + frequency + " = " + show(weekly))(
    RepoConfig(pullRequests = PullRequestsConfig(frequency = Some(weekly)))
  )}

## $updates

### $updates.$limit

If set, Scala Steward will only attempt to create or update `n` pull requests.
Useful if running frequently and / or CI build are costly.

$possibleValues `<positive integer>`

$default ${showCode(UpdatesConfig().limit)}

$examples

${verify(updates + "." + limit + " = " + "5")(
    RepoConfig(updates = UpdatesConfig(limit = Some(5)))
  )}

## $updatePullRequests

$possibleValues
  * ${showCode(Always: PullRequestUpdateStrategy)}:
    Scala Steward will always update the PR it created as long as you don't change it yourself.

  * ${showCode(Never: PullRequestUpdateStrategy)}:
    Scala Steward will never update the PR

  * ${showCode(OnConflicts: PullRequestUpdateStrategy)}:
    Scala Steward will update the PR it created to resolve conflicts as long as you don't change it yourself.

$default ${showCode(PullRequestUpdateStrategy.default)}

$examples

${verify(updatePullRequests + " = " + show(Always: PullRequestUpdateStrategy))(
    RepoConfig(updatePullRequests = Always)
  )}
""".trim + "\n"
}
