#!/bin/sh

set -ex

export JAVA_HOME="/opt/jre/current"
export PATH="$JAVA_HOME/bin:$PATH"

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
cd "$STEWARD_DIR"
git pull
sbt -no-colors ";clean ;core/assembly"
JAR=$(find -name "*assembly*.jar" | head -n1)

LOGIN="scala-steward"
java -jar ${JAR} \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email "me@$LOGIN.org" \
  --github-login ${LOGIN} \
  --git-ask-pass "$HOME/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --whitelist $HOME/.cache/coursier \
  --whitelist $HOME/.coursier \
  --whitelist $HOME/.ivy2 \
  --whitelist $HOME/.sbt \
  --whitelist $HOME/.scio-ideaPluginIC \
  --whitelist $JAVA_HOME \
  --read-only $JAVA_HOME
