#!/usr/bin/env bash
# Furnace 시작 스크립트. 빌드가 없으면 installDist 로 만든다(테스트는 돌리지 않는다).
#
# JVM 옵션 지정 (우선순위는 뒤에 쓴 것이 이긴다):
#   1) jvm.args 파일: 한 줄에 옵션 하나 (# 로 시작하는 줄은 주석)
#   2) 환경변수 JAVA_OPTS
#   3) 앞쪽 인자 중 -X, -D 로 시작하는 것은 JVM 옵션으로 간다 (그 뒤부터 서버 인자)
#      예) ./start.sh -Xmx4G -Dfurnace.sharedRegionPool=true --nogui
# -Xmx 를 아무 데서도 안 주면 기본 6G (FURNACE_XMX=4G 로 기본값만 바꿀 수도 있다).
set -e
cd "$(dirname "$0")"
APP=furnace-server/build/install/furnace-server
if [ ! -d "$APP/lib" ]; then
  ./gradlew -PskipOracle=true :furnace-server:installDist
fi

JVM_ARGS=()
if [ -f jvm.args ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; esac
    JVM_ARGS+=("$line")
  done < jvm.args
fi
# shellcheck disable=SC2206
[ -n "${JAVA_OPTS:-}" ] && JVM_ARGS+=(${JAVA_OPTS})
while [ $# -gt 0 ]; do
  case "$1" in
    -X*|-D*) JVM_ARGS+=("$1"); shift ;;
    *) break ;;
  esac
done
HAS_XMX=0
for a in "${JVM_ARGS[@]}"; do case "$a" in -Xmx*) HAS_XMX=1 ;; esac; done
[ "$HAS_XMX" = 0 ] && JVM_ARGS=("-Xmx${FURNACE_XMX:-6G}" "${JVM_ARGS[@]}")
exec java "${JVM_ARGS[@]}" -cp "$APP/lib/*" org.domi.furnace.server.FurnaceMain "$@"
