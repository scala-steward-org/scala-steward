#!/bin/bash -l

set -ex

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
echo -n $$ > "$STEWARD_DIR/scala-steward.pid"

cd "$STEWARD_DIR"
git pull
sbt -no-colors ";clean ;core/assembly"
JAR=$(find modules/ -name "scala-steward-assembly*.jar" | head -n1)

REPOS_FILE="$STEWARD_DIR/public-repos.md"
curl -s -o "$REPOS_FILE" https://raw.githubusercontent.com/scala-steward-org/repos/master/repos.md

# Don't start if we can't reach Maven Central.
curl -s --head --fail https://repo1.maven.org/maven2/

LOGIN="scala-steward"
java -DROOT_LOG_LEVEL=INFO -DLOG_LEVEL=INFO -jar ${JAR} \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$REPOS_FILE" \
  --git-author-email "me@$LOGIN.org" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "$HOME/.github/askpass/$LOGIN.sh" \
  --ignore-opts-files \
  --env-var "SBT_OPTS=-Xmx2048m -Xss8m -XX:MaxMetaspaceSize=512m" \
  --sign-commits \
  --cache-ttl 4hours \
  --process-timeout 20min \
  --whitelist $HOME/.cache/coursier \
  --whitelist $HOME/.coursier \
  --whitelist $HOME/.ivy2 \
  --whitelist $HOME/.sbt \
  --whitelist $HOME/.scio-ideaPluginIC \
  --whitelist $HOME/.tagless-redux-ijextPluginIC \
  --whitelist $JAVA_HOME \
  --read-only $JAVA_HOME
