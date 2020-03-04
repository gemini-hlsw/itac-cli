#!/bin/bash

# This re-copies sources from ../itac

echo 🔶  Setting up 'engine2'
rm -r modules/engine2/*
mkdir -p modules/engine2/src/main/scala
mkdir -p modules/engine2/src/main/java
mkdir -p modules/engine2/src/test/scala
mkdir -p modules/engine2/src/test/java

cp -r ../itac/queue-engine/qengine-api/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-ctx/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-impl/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-log/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-p1-io/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-p1/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-p2/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-skycalc/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/
cp -r ../itac/queue-engine/qengine-util/src/main/scala/edu/gemini/tac/qengine/* modules/engine2/src/main/scala/

cp -r ../itac/queue-engine/qengine-api/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-ctx/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-impl/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-log/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-p1-io/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-p1/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-p2/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-skycalc/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/
cp -r ../itac/queue-engine/qengine-util/src/main/java/edu/gemini/tac/qengine/* modules/engine2/src/main/java/


cp -r ../itac/queue-engine/qengine-api/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-ctx/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-impl/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-log/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-p1-io/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-p1/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-p2/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-skycalc/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/
cp -r ../itac/queue-engine/qengine-util/src/test/scala/edu/gemini/tac/qengine/* modules/engine2/src/test/scala/

cp -r ../itac/queue-engine/qengine-api/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-ctx/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-impl/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-log/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-p1-io/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-p1/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-p2/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-skycalc/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/
cp -r ../itac/queue-engine/qengine-util/src/test/java/edu/gemini/tac/qengine/* modules/engine2/src/test/java/

echo 🔶  Removing QueueCalculationLog
rm ./modules/engine2/src/main/scala/impl/QueueCalculationLog.scala


pushd modules/engine2/src

echo 🔶  Replacing 'Logger.getLogger' with 'org.slf4j.LoggerFactory.getLogger'
COMBY_M="$(cat <<"MATCH"
Logger.getLogger
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
org.slf4j.LoggerFactory.getLogger
REWRITE
)"
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Replacing 'QueueCalculationLog.logger' with 'org.slf4j.LoggerFactory.getLogger(getClass.getName)'
COMBY_M="$(cat <<"MATCH"
QueueCalculationLog.logger
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
org.slf4j.LoggerFactory.getLogger(getClass.getName)
REWRITE
)"
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Removing imports for 'java.util.logging'
COMBY_M="$(cat <<"MATCH"
import java.util.logging.:[syms\n]
MATCH
)"
comby "$COMBY_M" '' -i  .scala

echo 🔶  Removing imports for 'java.util.logging'
COMBY_M="$(cat <<"MATCH"
import org.apache.log4j.:[syms\n]
MATCH
)"
comby "$COMBY_M" '' -i .scala

echo 🔶  Rewriting '.log(Level.DEBUG, ...)' to '.debug(...)'
COMBY_M="$(cat <<"MATCH"
.log(Level.DEBUG, :[args])
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
.debug(:[args])
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Rewriting '.log(Level.FINE, ...)' to '.trace(...)'
COMBY_M="$(cat <<"MATCH"
.log(Level.FINE, :[args])
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
.trace(:[args])
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Rewriting '.log(Level.INFO, ...)' to '.info(...)'
COMBY_M="$(cat <<"MATCH"
.log(Level.INFO, :[args])
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
.info(:[args])
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Rewriting '.log(Level.ERROR, ...)' to '.error(...)'
COMBY_M="$(cat <<"MATCH"
.log(Level.ERROR, :[args])
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
.error(:[args])
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Rewriting ': Logger' to ': org.slf4j.Logger'
COMBY_M="$(cat <<"MATCH"
: Logger
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
: org.slf4j.Logger
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Fixing ProportionalPartnerSequence
COMBY_M="$(cat <<"MATCH"
LOGGER.debug(maxUnder)
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
LOGGER.debug(maxUnder.toString)
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R" -i .scala

echo 🔶  Fixing BlockIterator
COMBY_M="$(cat <<"MATCH"
LOGGER.debug(<Event:[args]>)
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
LOGGER.debug(<Event:[args]>.toString)
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R"  -i .scala

echo 🔶  Fixing ExplicitQueueTimeTest - 1
COMBY_M="$(cat <<"MATCH"
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])

MATCH
)"
COMBY_R="$(cat <<"REWRITE"


REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R"  .scala -i

echo 🔶  Fixing ExplicitQueueTimeTest - 2
COMBY_M="$(cat <<"MATCH"
with Checkers
MATCH
)"
COMBY_R="$(cat <<"REWRITE"
with org.scalatestplus.scalacheck.Checkers
REWRITE
)"
# Install comby with `bash <(curl -sL get.comby.dev)` or see github.com/comby-tools/comby && \
comby "$COMBY_M" "$COMBY_R"  .scala -i


popd

