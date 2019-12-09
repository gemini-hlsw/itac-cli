#!/bin/bash

# This re-copies sources from ../ocs3

echo 🔶  Setting up 'engine'
rm -rf modules/engine/src/
mkdir -p modules/engine/src/
rsync -au \
  ../itac/queue-engine/qengine-api/src/  \
  ../itac/queue-engine/qengine-ctx/src/  \
  ../itac/queue-engine/qengine-impl/src/  \
  ../itac/queue-engine/qengine-log/src/  \
  ../itac/queue-engine/qengine-p1-io/src/  \
  ../itac/queue-engine/qengine-p1/src/  \
  ../itac/queue-engine/qengine-p2/src/  \
  ../itac/queue-engine/qengine-skycalc/src/  \
  ../itac/queue-engine/qengine-util/src/  \
  modules/engine/src/

echo 🔶  Tweaking files
find modules -name *.java -exec sed -i '' -e 's/shared.skycalc/skycalc/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/shared.skycalc/skycalc/' {} \;
find modules -name *.java -exec sed -i '' -e 's/import edu.gemini.skycalc.SiteDesc;//' {} \;
mv modules/engine/src/main/java/edu/gemini/qengine/skycalc/SiteDescLookup.java \
   modules/engine/src/main/java/edu/gemini/qengine/skycalc/SiteLookup.java
find modules -name *.java -exec sed -i '' -e 's/SiteDescLookup/SiteLookup/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/SiteDescLookup/SiteLookup/' {} \;
find modules -name *.java -exec sed -i '' -e 's/SiteDesc/edu.gemini.spModel.core.Site/' {} \;
sed -i '' -e's/o.time/o.progTime/' modules/engine/src/main/scala/edu/gemini/tac/qengine/p1/io/ObservationIo.scala
find modules -name *.java -exec sed -i '' -e 's/getLatitude../latitude/' {} \;
find modules -name *.java -exec sed -i '' -e 's/getLongitude../longitude/' {} \;
find modules -name *.java -exec sed -i '' -e 's/org.apache.log4j/org.apache.logging.log4j/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/org.apache.log4j/org.apache.logging.log4j/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/Logger.getLogger/org.apache.logging.log4j.LogManager.getLogger/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/java.util.logging/org.apache.logging.log4j/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/Level.FINE/Level.TRACE/' {} \;
find modules -name *.scala -exec sed -i '' -e 's/QueueCalculationLog.logger/org.apache.logging.log4j.LogManager.getLogger("QueueCalculationLogger")/' {} \;
rm ./modules/engine/src/main/scala/edu/gemini/tac/qengine/impl/QueueCalculationLog.scala

