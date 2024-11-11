# CLI help

All command line arguments for the `scala-steward` application.

```
Usage:
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] --azure-repos --forge-api-host <uri> --forge-login <string> --git-ask-pass <file> [--add-labels] --azure-repos-organization <string> [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] --bitbucket [--forge-api-host <uri>] --forge-login <string> --git-ask-pass <file> [--do-not-fork] [--bitbucket-use-default-reviewers] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] --bitbucket-server --forge-api-host <uri> --forge-login <string> --git-ask-pass <file> [--bitbucket-server-use-default-reviewers] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] --gitlab [--forge-api-host <uri>] --forge-login <string> --git-ask-pass <file> [--do-not-fork] [--add-labels] [--gitlab-merge-when-pipeline-succeeds] [--gitlab-required-reviewers <integer>] [--gitlab-remove-source-branch] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] --gitea --forge-api-host <uri> --forge-login <string> --git-ask-pass <file> [--do-not-fork] [--add-labels] [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
    scala-steward --workspace <file> --repos-file <uri> [--repos-file <uri>]... [--git-author-name <string>] --git-author-email <string> [--git-author-signing-key <string>] [--sign-commits] [--signoff] [--github] [--forge-api-host <uri>] [--do-not-fork] [--add-labels] --github-app-id <integer> --github-app-key-file <file> [--ignore-opts-files] [--env-var <name=value>]... [--process-timeout <duration>] [--whitelist <string>]... [--read-only <string>]... [--enable-sandbox | --disable-sandbox] [--max-buffer-size <integer>] [--repo-config <uri>]... [--disable-default-repo-config] [--scalafix-migrations <uri>]... [--disable-default-scalafix-migrations] [--artifact-migrations <uri>]... [--disable-default-artifact-migrations] [--cache-ttl <duration>] [--url-checker-test-url <uri>]... [--default-maven-repo <string>] [--refresh-backoff-period <duration>] [--exit-code-success-if-any-repo-succeeds]
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
    --sign-commits
        Whether to sign commits; default: false
    --signoff
        Whether to signoff commits; default: false
    --azure-repos

    --forge-api-host <uri>
        API URL of the forge
    --vcs-api-host <uri>
        deprecated in favor of --forge-api-host
    --forge-login <string>
        The user name for the forge
    --vcs-login <string>
        deprecated in favor of --forge-login
    --git-ask-pass <file>
        An executable file that returns the git credentials
    --add-labels
        Whether to add labels on pull or merge requests (if supported by the forge)
    --azure-repos-organization <string>
        The Azure organization (required with --azure-repos)
    --bitbucket

    --do-not-fork
        Whether to not push the update branches to a fork; default: false
    --bitbucket-use-default-reviewers
        Whether to assign the default reviewers to a bitbucket pull request; default: false
    --bitbucket-server

    --bitbucket-server-use-default-reviewers
        Whether to assign the default reviewers to a bitbucket server pull request; default: false
    --gitlab

    --gitlab-merge-when-pipeline-succeeds
        Whether to merge a gitlab merge request when the pipeline succeeds
    --gitlab-required-reviewers <integer>
        When set, the number of required reviewers for a merge request will be set to this number (non-negative integer).  Is only used in the context of gitlab-merge-when-pipeline-succeeds being enabled, and requires that the configured access token have the appropriate privileges.  Also requires a Gitlab Premium subscription.
    --gitlab-remove-source-branch
        Flag indicating if a merge request should remove the source branch when merging.
    --gitea

    --github

    --github-app-id <integer>
        GitHub application id. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required.
    --github-app-key-file <file>
        GitHub application key file. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required.
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
    --url-checker-test-url <uri>
        URL for testing the UrlChecker at start-up (can be used multiple times); default: https://github.com
    --default-maven-repo <string>
        default: https://repo1.maven.org/maven2/
    --refresh-backoff-period <duration>
        Period of time a failed build won't be triggered again; default: 0days
    --exit-code-success-if-any-repo-succeeds
        Whether the Scala Steward process should exit with success (exit code 0) if any repo succeeds; default: false

Subcommands:
    validate-repo-config
        Validate the repo config file and exit; report errors if any
```
