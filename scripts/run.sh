#!/bin/sh

set -ex

#export PATH="/opt/jre/current/bin:$PATH"
export PATH="/opt/graalvm/current/bin:$PATH"
SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
cd "$STEWARD_DIR"
git pull
export SBT_OPTS="-Xms256M -Xmx768M"
sbt -no-colors ";clean ;core/assembly"
JAR=$(find -name "*assembly*.jar" | head -n1)
java -jar $JAR
