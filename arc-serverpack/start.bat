@echo off
:: Arc Server Start Script
:: Requires Java 21+

set MEMORY_MIN=512M
set MEMORY_MAX=4G

for %%f in (arc-*.jar) do set JAR=%%f
if not defined JAR (
    echo Error: No arc-*.jar found in this directory
    pause
    exit /b 1
)

java ^
  -Xms%MEMORY_MIN% ^
  -Xmx%MEMORY_MAX% ^
  -XX:+UseG1GC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+DisableExplicitGC ^
  -XX:+AlwaysPreTouch ^
  -XX:G1HeapWastePercent=5 ^
  -XX:G1MixedGCCountTarget=4 ^
  -XX:InitiatingHeapOccupancyPercent=15 ^
  -XX:G1MixedGCLiveThresholdPercent=90 ^
  -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:SurvivorRatio=32 ^
  -XX:+PerfDisableSharedMem ^
  -XX:MaxTenuringThreshold=1 ^
  -Dusing.aikars.flags=https://mcflags.emc.gs ^
  -Daikars.new.flags=true ^
  -jar "%JAR%" nogui

pause
