#!/bin/sh

export PATH=$PATH:/opt/jre/current/bin
export SBT_OPTS="-Xmx512M

STEWARD_DIR=/home/frank/code/scala-steward
cd $STEWARD_DIR
git pull
sbt core/run > steward.log
