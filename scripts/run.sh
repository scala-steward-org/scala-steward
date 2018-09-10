#!/bin/sh

export PATH=$PATH:/opt/jre/current/bin
STEWARD_DIR=/home/frank/code/scala-steward
cd $STEWARD_DIR
git pull
sbt core/run > steward.log
