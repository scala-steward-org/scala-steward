# CLI help

All command line arguments for the `scala-steward` application.

### Mandatory arguments
```
  --workspace  DIRECTORY     Location for cache and temporary files
  --repos-file  FILE         A markdown formatted file with a repository list
  --git-author-email  EMAIL  Email address of the git user
  --vcs-login  USERNAME      The user name for the git hoster
  --git-ask-pass  FILE       An executable file that returns the git credentials
```

### Optional arguments
```
  --default-repo-conf  FILE                          A .scala-steward.conf file with default values
  --git-author-name  NAME                            Git "user.name", default: "Scala Steward"
  --git-author-signing-key  KEY                      Git "user.signingKey"
  --vcs-type  VCS_TYPE                               One of "github", "gitlab", "bitbucket" or "bitbucket-server", default: "github"
  --vcs-api-host  URI                                API uri of the git hoster, default: "https://api.github.com"
  --sign-commits  BOOLEAN                            Wether to sign commits, default: "false"
  --whitelist  DIRECTORY                             Directory white listed for the sandbox (can be used multiple times)
  --read-only  DIRECTORY                             Read only directory for the sandbox (can be used multiple times)
  --enable-sandbox  BOOLEAN                          Wether to use the sandbox, overwrites "--disable-sandbox", default: "false"
  --disable-sandbox  BOOLEAN                         Wether to not use the sandbox, default: "true"
  --do-not-fork  BOOLEAN                             Wether to not push the update branches to a fork, default: "false"
  --ignore-opts-files  BOOLEAN                       Wether to remove ".jvmopts" and ".sbtopts" files before invoking the build tool  
  --env-var VAR=VALUE                                Assigns the VALUE to the environment variable VAR (can be used multiple times)
  --process-timeout  DURATION                        Timeout for external process invokations, default: "10min"
  --max-buffer-size  LINES                           Size of the buffer for the output of an external process in lines, default: "8192"
  --scalafix-migrations  URI                         Additional scalafix migrations configuraton file (can be used multiple times)
  --disable-default-scalafix-migrations  BOOLEAN     Wether to disable the default scalafix migration file
  --artifact-migrations  FILE                        An additional file with artifact migration configurations
  --cache-ttl  DURATION                              TTL for the caches, default: "2hours"
  --bitbucket-server-use-default-reviewers  BOOLEAN  Wether to assign the default reviewers to a bitbucket pull request, default: "false"
  --gitlab-merge-when-pipeline-succeeds  BOOLEAN     Wether to merge a gitlab merge request when the pipeline succeeds
  --github-app-key-file  FILE                        Github application key file
  --github-app-id  ID                                Github application id
```
