#!/usr/bin/env bash
# Furnace 시작 스크립트. 빌드가 없으면 installDist 로 만든다. 기본 힙 -Xmx6G (바꾸려면 FURNACE_XMX=4G ./start.sh).
set -e
cd "$(dirname "$0")"
APP=furnace-server/build/install/furnace-server
if [ ! -d "$APP/lib" ]; then
  ./gradlew -PskipOracle=true :furnace-server:installDist
fi
exec java -Xmx"${FURNACE_XMX:-6G}" ${JAVA_OPTS} -cp "$APP/lib/*" org.domi.furnace.server.FurnaceMain "$@"
