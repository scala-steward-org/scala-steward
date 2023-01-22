# CLI help

All command line arguments for the `scala-steward` application.

```
Usage:
    scala-steward validate-repo-config
    scala-steward --workspace <file> --repos-file <file> [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] --git-ask-pass <file> [--sign-commits] [--forge-type <forge-type>] [--forge-api-host <uri>] --forge-login <string> [--do-not-fork] [--add-labels] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--bitbucket-use-default-reviewers] [--bitbucket-server-use-default-reviewers] [--gitlab-merge-when-pipeline-succeeds] [--gitlab-required-reviewers <integer>] [--azure-repos-organization <string>] [--github-app-id <integer> --github-app-key-file <file>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>]



Options and flags:
    --help
        Display this help text.
    --workspace <file>
        Location for cache and temporary files
    --repos-file <file>
        A markdown formatted file with a repository list
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
    --forge-type <forge-type>
        One of azure-repos, bitbucket, bitbucket-server, github, gitlab; default: github
    --vcs-type <forge-type>
        deprecated in favor of --forge-type
    --forge-api-host <uri>
        API URL of the forge; default: https://api.github.com
    --vcs-api-host <uri>
        deprecated in favor of --forge-api-host
    --forge-login <string>
        The user name for the forge
    --vcs-login <string>
        deprecated in favor of --forge-login
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
        Size of the buffer for the output of an external process in lines; default: 16384
    --repo-config <uri>
        Additional repo config file (can be used multiple times)
    --disable-default-repo-config
        Whether to disable the default repo config file
    --scalafix-migrations <uri>
        Additional scalafix migrations configuration file (can be used multiple times)
    --disable-default-scalafix-migrations
        Whether to disable the default scalafix migration file; default: false
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
    --azure-repos-organization <string>
        The Azure organization (required when --forge-type is azure-repos)
    --github-app-id <integer>
        GitHub application id
    --github-app-key-file <file>
        GitHub application key file
    --url-checker-test-url <uri>
        URL for testing the UrlChecker at start-up (can be used multiple times); default: https://github.com
    --default-maven-repo <string>
        default: https://repo1.maven.org/maven2/
    --refresh-backoff-period <duration>
        Period of time a failed build won't be triggered again; default: 0days

Subcommands:
    validate-repo-config
        Validate the repo config file and exit; report errors if any
```
