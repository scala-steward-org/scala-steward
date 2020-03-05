# .scala-steward.conf

You can add `<YOUR_REPO>/.scala-steward.conf` to configure how Scala Steward updates your repository.

## commits

### commits.message

If set, Scala Steward will use this message template for the commit messages and PR titles.
Supported variables: ${artifactName}, ${currentVersion}, ${nextVersion} and ${default}

*Default:* "${default}" which is equivalent to "Update ${artifactName} to ${nextVersion}" 

```properties
commits.message = "Update ${artifactName} from ${currentVersion} to ${nextVersion}"
```

## pullRequests

### pullRequests.frequency

Allows to control how often or when Scala Steward is allowed to create pull requests.

*Possible values:*
 * `"@asap"`

   PRs are created without delay.

 * `"@daily"` | `"@weekly"`  | `"@monthly"`

   PRs are created at least 1 day | 7 days | 30 days after the last PR.

 * `"<CRON expression>"`

   PRs are created roughly according to the given CRON expression.

   CRON expressions consist of five fields:
   minutes, hour of day, day of month, month, and day of week.

   See https://www.alonsodomin.me/cron4s/userguide/index.html#parsing for
   more information about the CRON expressions that are supported.

   Note that the date parts of the CRON expression are matched exactly while the the time parts are only used to abide to the frequency of the given expression.


*Default:* `"@asap"`

*Examples:*

```properties
pullRequests.frequency = "@weekly"
```

## updates

### updates.limit

If set, Scala Steward will only attempt to create or update `n` pull requests.
Useful if running frequently and / or CI build are costly.

*Possible values:* `<positive integer>`

*Default:* `null`

*Examples:*

```properties
updates.limit = 5
```

## updatePullRequests

*Possible values:*
  * `"always"`:
    Scala Steward will always update the PR it created as long as you don't change it yourself.

  * `"never"`:
    Scala Steward will never update the PR

  * `"on-conflicts"`:
    Scala Steward will update the PR it created to resolve conflicts as long as you don't change it yourself.

*Default:* `"on-conflicts"`

*Examples:*

```properties
updatePullRequests = "always"
```
