# CLI help

All command line arguments for the `scala-steward` application.

Usage: args [options]

Options:
  --workspace file                          Location for cache and temporary files
  --repos-file file                         A markdown formatted file with a repository list
  --git-author-name string                  Git "user.name", default: "Scala Steward"
  --git-author-email string                 Email address of the git user
  --git-author-signing-key string?          Git "user.signingKey"
  --vcs-type vcs-type                       One of "github", "gitlab", "bitbucket" or "bitbucket-server", default: "github"
  --vcs-api-host uri                        API uri of the git hoster, default: "https://api.github.com"
  --vcs-login string                        The user name for the git hoster
  --git-ask-pass file                       An executable file that returns the git credentials
  --sign-commits                            Whether to sign commits, default: "false"
  --whitelist string*                       Directory white listed for the sandbox (can be used multiple times)
  --read-only string*                       Read only directory for the sandbox (can be used multiple times)
  --enable-sandbox                          Whether to use the sandbox, overwrites "--disable-sandbox", default: "false"
  --disable-sandbox                         Whether to not use the sandbox, default: "true"
  --do-not-fork                             Whether to not push the update branches to a fork, default: "false"
  --ignore-opts-files                       Whether to remove ".jvmopts" and ".sbtopts" files before invoking the build tool
  --env-var name=value*                     Assigns the value to the environment variable name (can be used multiple times)
  --process-timeout duration                Timeout for external process invocations, default: "10min"
  --max-buffer-size int                     Size of the buffer for the output of an external process in lines, default: "8192"
  --repo-config uri*                        Additional repo config file (can be used multiple times)
  --disable-default-repo-config             Whether to disable the default repo config file
  --scalafix-migrations uri*                Additional scalafix migrations configuration file (can be used multiple times)
  --disable-default-scalafix-migrations     Whether to disable the default scalafix migration file
  --artifact-migrations uri*                Additional artifact migration configuration file (can be used multiple times)
  --disable-default-artifact-migrations     Whether to disable the default artifact migration file
  --cache-ttl duration                      TTL for the caches, default: "2hours"
  --bitbucket-server-use-default-reviewers  Whether to assign the default reviewers to a bitbucket pull request, default: "false"
  --gitlab-merge-when-pipeline-succeeds     Whether to merge a gitlab merge request when the pipeline succeeds
  --github-app-key-file file?               GitHub application key file
  --github-app-id long?                     GitHub application id
  --url-checker-test-url uri?
  --default-maven-repo string?
  --refresh-backoff-period duration         Period of time a failed build won't be triggered again, default: "0days"
