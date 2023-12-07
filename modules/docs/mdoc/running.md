## Running Scala Steward

A complete list of all command line arguments can be found [here](help.md).

```bash
sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --repo-config "$STEWARD_DIR/default.scala-steward.conf" \
  --git-author-email ${EMAIL} \
  --forge-api-host "https://api.github.com" \
  --forge-login ${LOGIN} \
  --git-ask-pass "$STEWARD_DIR/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

Or as a [Docker](https://www.docker.com/) container:

```bash
sbt docker:publishLocal

docker run -v $STEWARD_DIR:/opt/scala-steward -it fthomas/scala-steward:latest \
  --workspace  "/opt/scala-steward/workspace" \
  --repos-file "/opt/scala-steward/repos.md" \
  --repo-config "/opt/scala-steward/default.scala-steward.conf" \
  --git-author-email ${EMAIL} \
  --forge-api-host "https://api.github.com" \
  --forge-login ${LOGIN} \
  --git-ask-pass "/opt/scala-steward/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR \ 
  --scalafix-migrations "/opt/scala-steward/extra-scalafix-migrations.conf" \
  --artifact-migrations "/opt/scala-steward/extra-artifact-migrations.conf" 
```

The [`git-ask-pass` option](https://git-scm.com/docs/gitcredentials) must specify an executable file (script) that returns (on the stdout),

- either the plain text password corresponding to the configured `${LOGIN}`,
- or (recommended) an authentication token corresponding to `${LOGIN}` (with appropriate permissions to watch the repositories; e.g. [Create a personal access token](https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line) for GitHub).

**Note about git-ask-pass option**: The provided script must start with a valid shebang like `#!/bin/sh`, see issue [#1374](@GITHUB_URL@/issues/1374)

More information about using the `--scalafix-migrations` and `--artifact-migrations` options can be found 
[here][scalafixmigrations] and [here][artifactmigrations].

### Workspace

The workspace directory (specified with `--workspace`) provides a location for cache and temporary files.  

It is important to persist this workspace between runs.  Without this, Scala Steward will be unable to observe 
repo-specific preferences (such as [pullRequests.frequency](repo-specific-configuration.md)) correctly.   

### Private repositories

If you run Scala Steward for your own private projects, the option `--do-not-fork` can be required, not to fork.
Instead it will create pull requests directly on the private repository (as soon as the `${LOGIN}` can).

#### Credentials using environment variables

It can also be useful to pass additional environment variables from the command line using the `--env-var` flag as shown in the examples above. You can use this to pass any credentials required by your projects to resolve any private dependencies, e.g.:

```bash
--env-var BINTRAY_USER=username \
--env-var BINTRAY_PASS=password
```

These variables will be accessible (in sbt) to all of the projects that Scala Steward checks dependencies for.

#### Credentials using a credentials.sbt file

If your projects require credentials, you can also provide global credentials in the `$HOME/.sbt/1.0/credentials.sbt` file. 
The file should contain a single line: `credentials += Credentials("Some Nexus Repository Manager", "my.artifact.repo.net", "admin", "admin123")`.

#### sbt 0.13 workaround
For sbt 0.13 builds, scala-steward [may be unable](https://gitter.im/fthomas/scala-steward?at=5f0573dac7d15f7d0f7b15ac) to extract credentials for private resolvers. Instead, you can [configure coursier directly](https://get-coursier.io/docs/other-credentials) by adding `~/.config/coursier/credentials.properties`:
```scala
example1.username=username
example1.password=password
example1.host=artifacts.example.com
example1.realm=Example Realm
```

#### Running behind a proxy

You can configure a proxy using the JAVA_OPTS environment variable with proxy properties.

For example:

```bash
JAVA_OPTS="-Dhttp.proxyHost=webcache.example.com -Dhttp.proxyPort=8080 -Dhttps.proxyHost=webcache.example.com -Dhttps.proxyPort=8080"
```

See Oracle proxies documentation for more info.

### Running locally from sbt

#### Sample run for GitLab

```
sbt
project core
run --do-not-fork --workspace "/path/workspace" --repos-file "/path/repos.md" --repo-config "/path/default.scala-steward.conf" --git-ask-pass "/path/pass.sh" --git-author-email "email@example.org" --forge-type "gitlab" --forge-api-host "https://gitlab.com/api/v4/" --forge-login "gitlab.steward"
```


### Running on Docker for Bitbucket

* Create a file `repos.md` that will be injected into the container as as volume.
* Create a file `run.sh` with this content:

```
echo "#!/bin/sh"                  >> pass.sh
echo "echo '$BITBUCKET_PASSWORD'" >> pass.sh

chmod +x pass.sh

docker run -v $PWD:/opt/scala-steward \
    -v ~/.sbt/:/root/.sbt \
    -it fthomas/scala-steward:latest \
    -DLOG_LEVEL=TRACE \
    --do-not-fork \
    --workspace "/opt/scala-steward/workspace" \
    --repos-file "/opt/scala-steward/repos.md" \
    --repo-config "/opt/scala-steward/default.scala-steward.conf" \
    --git-ask-pass "/opt/scala-steward/pass.sh" \
    --git-author-email "myemail@company.xyz" \
    --forge-type "bitbucket" \
    --forge-api-host "https://api.bitbucket.org/2.0" \
    --forge-login "$BITBUCKET_USERNAME"
```

* Run it from a CI tool or manually using with this command:

`BITBUCKET_USERNAME=<myuser> BITBUCKET_PASSWORD=<mypass> ./run.sh`

### Running in a Bitbucket pipeline to update Bitbucket repos

* Create a file `repos.md` that will be injected into the container as a volume.
* Create a file `run.sh` with this content:

```
echo "#!/bin/sh"                  >> pass.sh
echo "echo '$BITBUCKET_PASSWORD'" >> pass.sh

chmod +x pass.sh

docker run -v $PWD:/opt/scala-steward \
    -i fthomas/scala-steward:latest \
    -DLOG_LEVEL=TRACE \
    --do-not-fork \
    --workspace "/opt/scala-steward/workspace" \
    --repos-file "/opt/scala-steward/repos.md" \
    --repo-config "/opt/scala-steward/default.scala-steward.conf" \
    --git-ask-pass "/opt/scala-steward/pass.sh" \
    --git-author-email "myemail@company.xyz" \
    --forge-type "bitbucket" \
    --forge-api-host "https://api.bitbucket.org/2.0" \
    --forge-login "$BITBUCKET_USERNAME"
```

NOTE: This script is slightly different to the one in the previous Bitbucket
example, because it needs to run in a Bitbucket Pipeline. The `-t` flag has been
removed, and we do mount `~/.sbt` as a volume.

* Prepare an S3 bucket (or similar storage) to persist the Scala Steward
  workspace between runs
* Set some repository variables: AWS credentials, plus the S3 bucket name
* Create a pipeline to run Scala Steward and sync the workspace to S3:

```
image:
  name: <any Linux image with AWS CLI installed>

options:
  docker: true

definitions:
  services:
    docker:
      memory: 4096

pipelines:
  custom:
    run-scala-steward:
      - step:
          name: Run Scala Steward
          size: 2x
          script:
            - aws s3 sync s3://${WORKSPACE_BUCKET}/workspace ./workspace
            - ./run.sh
            - aws s3 sync ./workspace s3://${WORKSPACE_BUCKET}/workspace
```

* In the Pipelines UI, configure the pipeline to run on a schedule (e.g. daily)

### Running On-premise

#### GitHub Enterprise

There is multiple articles on how to run Scala Steward on-premise:

* [Running Scala Steward On-premise](https://engineering.avast.io/running-scala-steward-on-premise)
* [Running scala-steward periodically on AWS Fargate](https://medium.com/@tanishiking/running-scala-steward-periodically-on-aws-fargate-3d3d202f0f7)
* [Scala StewardとGitHub Actionsで依存ライブラリの更新を自動化する](https://scalapedia.com/articles/145/Scala+Steward%E3%81%A8GitHub+Actions%E3%81%A7%E4%BE%9D%E5%AD%98%E3%83%A9%E3%82%A4%E3%83%96%E3%83%A9%E3%83%AA%E3%81%AE%E6%9B%B4%E6%96%B0%E3%82%92%E8%87%AA%E5%8B%95%E5%8C%96%E3%81%99%E3%82%8B)
* [Centralized Scala Steward with GitHub Actions](https://hector.dev/2020/11/18/centralized-scala-steward-with-github-actions)
* [Big Timesavers for Busy Scala Developers](https://speakerdeck.com/exoego/big-timesavers-for-busy-scala-developers)
* [Running scala steward on private repos](http://www.roundcrisis.com/2020/08/15/Scala-Steward-locally/)
  
#### GitLab

The following describes a setup using GitLab Docker runner, which you have to set up separately.

1. create a "scalasteward" user in GitLab
2. assign that user "Developer" permissions in every project that should be managed by Scala Steward
3. login as that user and create a Personal Access Token with `api`, `read_repository` and `write_repository` scopes
4. create a project and add the following GitLab CI config

```yaml
check:
  rules:
    # only run when scheduled, or when pushing a commit to the default
    # branch which changed the repos.md file
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      when: never
    - if: '$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH'
      changes:
        - repos.md
      when: on_success
    - if: '$CI_PIPELINE_SOURCE == "schedule"'
      when: on_success
  image:
    name: fthomas/scala-steward:latest
    entrypoint: [""]
  variables:
    # change values here, if needed;
    EMAIL: 'me@scala-steward.org'
    LOGIN: 'scalasteward'
  script:
    - mkdir --parents "$CI_PROJECT_DIR/.sbt" "$CI_PROJECT_DIR/.ivy2"
    - ln -sfT "$CI_PROJECT_DIR/.sbt"  "$HOME/.sbt"
    - ln -sfT "$CI_PROJECT_DIR/.ivy2" "$HOME/.ivy2"
    - chmod +x "$CI_PROJECT_DIR/askpass.sh"
    - >
      /opt/docker/bin/scala-steward \
        --workspace "$CI_PROJECT_DIR/workspace" \
        --process-timeout "30min" \
        --do-not-fork \
        --repos-file "$CI_PROJECT_DIR/repos.md" \
        --repo-config "$CI_PROJECT_DIR/default.scala-steward.conf" \
        --git-author-email "${EMAIL}" \
        --forge-type "gitlab" \
        --forge-api-host "${CI_API_V4_URL}" \
        --forge-login "${LOGIN}" \
        --git-ask-pass "$CI_PROJECT_DIR/askpass.sh"
  cache:
    key: scala-steward
    paths:
      - .ivy2/cache
      - .sbt/boot/scala*
      - workspace/store
```
5. add a masked CI variable `SCALA_STEWARD_TOKEN` in "Settings > CI / CD : Variables" for the access token
6. add the `askpass.sh` script to the repository:

```bash
#!/usr/bin/env bash

echo "${SCALA_STEWARD_TOKEN}"
```
7. add the `repos.md` file 
8. (*optional*) create a new schedule to trigger the pipeline on a daily/weekly basis

Scala Steward is compatible with Coursier authentication using headers. To authenticate
using the [Gitlab CI/CD job token](https://docs.gitlab.com/ee/ci/jobs/ci_job_token.html), while also supporting your own private token when performing
local development, use the following snippet:
```scala
import lmcoursier.CoursierConfiguration
import lmcoursier.definitions.Authentication

lazy val gitlabToken: Option[(String, String)] = {
  //The Gitlab runner sets CI_JOB_TOKEN automatically as part of running inside a build job
  val jobToken = sys.env.get("CI_JOB_TOKEN").map(t => ("Job-Token", t)) 
  //When running on your local machine, set the environment variable GITLAB_PRIVATE_TOKEN
  val privateToken = sys.env.get("GITLAB_PRIVATE_TOKEN").map(t => ("Private-Token", t))

  jobToken.orElse(privateToken)
}

def addGitlabToken(current: CoursierConfiguration): CoursierConfiguration = {
  gitlabToken.fold(current) { token =>
    current.addRepositoryAuthentication("gitlab-repo", Authentication(Seq(token)))
  }
}

resolvers += "gitlab-repo" at s"https://gitlab.example.com/api/v4/groups/1/-/packages/maven"
csrConfiguration ~= addGitlabToken
updateClassifiers / csrConfiguration ~= addGitlabToken
updateSbtClassifiers / csrConfiguration ~= addGitlabToken
```

#### Azure Repos

* Create a file `repos.md` that will be injected into the container as a volume.
* Create a file `run.sh` with this content:

```
echo "#!/bin/sh"                  >> pass.sh
echo "echo '$AZURE_REPO_ACCESS_TOKEN'" >> pass.sh

chmod +x pass.sh

docker run -v $PWD:/opt/scala-steward \
    -it fthomas/scala-steward:latest \
    -DLOG_LEVEL=TRACE \
    --do-not-fork \
    --workspace "/opt/scala-steward/workspace" \
    --repos-file "/opt/scala-steward/repos.md" \
    --git-author-email "email@mycompany.com" \
    --forge-type "azure-repos" \
    --forge-api-host "https://dev.azure.com" \
    --forge-login "email@mycompany.com" \
    --azure-repos-organization "mycompany" \
    --git-ask-pass "/opt/scala-steward/pass.sh"
```

Note: `AZURE_REPO_ACCESS_TOKEN` is a personal access token created with Read, write, & manage permissions to your repositories.

[scalafixmigrations]: @GITHUB_URL@/blob/@MAIN_BRANCH@/docs/scalafix-migrations.md
[artifactmigrations]: @GITHUB_URL@/blob/@MAIN_BRANCH@/docs/artifact-migrations.md
