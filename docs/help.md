# CLI help

All command line arguments for the `scala-steward` application.

```
Usage:
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] --git-ask-pass <file> [--sign-commits] [--signoff] [--forge-type <forge-type>] [--forge-api-host <uri>] --forge-login <string> [--do-not-fork] [--add-labels] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--bitbucket-use-default-reviewers] [--bitbucket-server-use-default-reviewers] [--gitlab-merge-when-pipeline-succeeds] [--gitlab-required-reviewers <integer>] [--gitlab-remove-source-branch] [--azure-repos-organization <string>] [--github-app-id <integer> --github-app-key-file <file>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>]... [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward validate-repo-config



Options and flags:
    --help
        Display this help text.
    --workspace <file>
        Location for cache and temporary files
    --repos-file <uri>
        A markdown formatted file with a repository list (can be used multiple times)
    --git-author-name <string>
        Git "user.name"; default: Scala Steward
    --git-author-email <string>
        Git "user.email"
    --git-author-signing-key <string>
        Git "user.signingKey"
    --git-ask-pass <file>
        An executable file that returns the git credentials
    --sign-commits
        Whether to sign commits; default: false
    --signoff
        Whether to signoff commits; default: false
    --forge-type <forge-type>
        One of azure-repos, bitbucket, bitbucket-server, github, gitlab, gitea; default: github
    --forge-api-host <uri>
        API URL of the forge; default: https://api.github.com
    --forge-login <string>
        The user name for the forge
    --do-not-fork
        Whether to not push the update branches to a fork; default: false
    --add-labels
        Whether to add labels on pull or merge requests (if supported by the forge)
    --ignore-opts-files
        Whether to remove ".jvmopts" and ".sbtopts" files before invoking the build tool
    --env-var <name=value>
        Assigns the value to the environment variable name (can be used multiple times)
    --process-timeout <duration>
        Timeout for external process invocations; default: 10minutes
    --whitelist <string>
        Directory white listed for the sandbox (can be used multiple times)
    --read-only <string>
        Read only directory for the sandbox (can be used multiple times)
    --enable-sandbox
        Whether to use the sandbox
    --disable-sandbox
        Whether to not use the sandbox
    --max-buffer-size <integer>
        Size of the buffer for the output of an external process in lines; default: 32768
    --repo-config <uri>
        Additional repo config file (can be used multiple times)
    --disable-default-repo-config
        Whether to disable the default repo config file
    --scalafix-migrations <uri>
        Additional Scalafix migrations configuration file (can be used multiple times)
    --disable-default-scalafix-migrations
        Whether to disable the default Scalafix migration file; default: false
    --artifact-migrations <uri>
        Additional artifact migration configuration file (can be used multiple times)
    --disable-default-artifact-migrations
        Whether to disable the default artifact migration file
    --cache-ttl <duration>
        TTL for the caches; default: 2hours
    --bitbucket-use-default-reviewers
        Whether to assign the default reviewers to a bitbucket pull request; default: false
    --bitbucket-server-use-default-reviewers
        Whether to assign the default reviewers to a bitbucket server pull request; default: false
    --gitlab-merge-when-pipeline-succeeds
        Whether to merge a gitlab merge request when the pipeline succeeds
    --gitlab-required-reviewers <integer>
        When set, the number of required reviewers for a merge request will be set to this number (non-negative integer).  Is only used in the context of gitlab-merge-when-pipeline-succeeds being enabled, and requires that the configured access token have the appropriate privileges.  Also requires a Gitlab Premium subscription.
    --gitlab-remove-source-branch
        Flag indicating if a merge request should remove the source branch when merging.
    --azure-repos-organization <string>
        The Azure organization (required when --forge-type is azure-repos)
    --github-app-id <integer>
        GitHub application id. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required.
    --github-app-key-file <file>
        GitHub application key file. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required.
    --url-checker-test-url <uri>
        URL for testing the UrlChecker at start-up (can be used multiple times); default: https://github.com
    --default-maven-repo <string>
        (can be used multiple times); default: https://repo1.maven.org/maven2/
    --refresh-backoff-period <duration>
        Period of time a failed build won't be triggered again; default: 0days
    --exit-code-success-if-any-repo-succeeds
        Whether the Scala Steward process should exit with success (exit code 0) if any repo succeeds; default: false

Subcommands:
    validate-repo-config
        Validate the repo config file and exit; report errors if any
```
