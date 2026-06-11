#!/usr/bin/env bash
# Arc Server Start Script
# Requires Java 21+

MEMORY_MIN="512M"
MEMORY_MAX="4G"
JAR=$(ls arc-*.jar 2>/dev/null | head -1)

if [ -z "$JAR" ]; then
    echo "Error: No arc-*.jar found in this directory"
    exit 1
fi

java \
  -Xms${MEMORY_MIN} \
  -Xmx${MEMORY_MAX} \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar "$JAR" nogui
