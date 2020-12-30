## Running Scala Steward

A complete list of all command line arguments can be found [here](help.md).

```bash
sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --default-repo-conf "$STEWARD_DIR/default.scala-steward.conf" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
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
  --default-repo-conf "/opt/scala-steward/default.scala-steward.conf" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "/opt/scala-steward/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR \ 
  --scalafix-migrations "/opt/scala-steward/extra-scalafix-migrations.conf" \
  --artifact-migrations "/opt/scala-steward/extra-artifact-migrations.conf" 
```

The [`git-ask-pass` option](https://git-scm.com/docs/gitcredentials) must specify an executable file (script) that returns (on the stdout),

- either the plain text password corresponding to the configured `${LOGIN}`,
- or (recommended) an authentication token corresponding to `${LOGIN}` (with appropriate permissions to watch the repositories; e.g. [Create a personal access token](https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line) for GitHub).

**Note about git-ask-pass option**: The provided script must start with a valid shebang like `#!/bin/sh`, see issue [#1374](/../../issues/1374)

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

### Running locally from sbt

#### Sample run for GitLab

```
sbt
project core
run --do-not-fork --workspace "/path/workspace" --repos-file "/path/repos.md" --default-repo-conf "/path/default.scala-steward.conf" --git-ask-pass "/path/pass.sh" --git-author-email "email@example.org" --vcs-type "gitlab" --vcs-api-host "https://gitlab.com/api/v4/" --vcs-login "gitlab.steward"
```


#### Running on Docker for Bitbucket

* Create a file `repos.md` that will be injected into the container as as volume.
* Create a file `run.sh` with this content:

```
echo "#!/bin/sh"                  >> pass.sh  
echo "echo '$BITBUCKET_PASSWORD'" >> pass.sh

chmod +x pass.sh

docker run -v $PWD:/opt/scala-steward \
    -v ~/.sbt/:/root/.sbt \
    -it fthomas/scala-steward:latest \
    --env-var LOG_LEVEL=TRACE \
    --do-not-fork \
    --workspace "/opt/scala-steward/workspace" \
    --repos-file "/opt/scala-steward/repos.md" \
    --default-repo-conf "/opt/scala-steward/default.scala-steward.conf" \
    --git-ask-pass "/opt/scala-steward/pass.sh" \
    --git-author-email "myemail@company.xyz" \
    --vcs-type "bitbucket" \
    --vcs-api-host "https://api.bitbucket.org/2.0" \
    --vcs-login "$BITBUCKET_USERNAME"
    
```

* Run it from a CI tool or manually using with this command:

`BITBUCKET_USERNAME=<myuser> BITBUCKET_PASSWORD=<mypass> ./run.sh`

### Running On-premise

#### GitHub Enterprise

There is an article on how they run Scala Steward on-premise at Avast:
* [Running Scala Steward On-premise](https://engineering.avast.io/running-scala-steward-on-premise)

#### GitLab

The following describes a setup using GitLab Docker runner, which you have to setup seperately.

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
    - >-
      /opt/docker/bin/scala-steward
        --workspace  "$CI_PROJECT_DIR/workspace"
        --process-timeout 30min
        --do-not-fork
        --repos-file "$CI_PROJECT_DIR/repos.md"
        --default-repo-conf "$CI_PROJECT_DIR/default.scala-steward.conf"
        --git-author-email "${EMAIL}"
        --vcs-type "gitlab"
        --vcs-api-host "${CI_API_V4_URL}"
        --vcs-login "${LOGIN}"
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


[scalafixmigrations]: https://github.com/scala-steward-org/scala-steward/blob/master/docs/scalafix-migrations.md
[artifactmigrations]: https://github.com/scala-steward-org/scala-steward/blob/master/docs/artifact-migrations.md
