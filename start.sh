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

# AppCDS: JDK 19 이상이면 클래스 공유 아카이브를 자동으로 만들고(처음 끌 때 저장) 다음 기동부터 쓴다.
# 끄려면 FURNACE_CDS=0, 직접 지정한 옵션이 있으면 그대로 둔다.
# 아카이브가 lib 의 jar 보다 오래됐으면 지운다(JDK 21 은 낡은 아카이브를 다시 만들지 않는다).
# 헤더가 깨진 부분 파일(첫 덤프 중 강제 종료)도 지운다. 설치 폴더가 쓸 수 없고 아카이브도 없으면 옵션을 붙이지 않는다.
CDS_ARGS=()
CDS_FILE="$APP/furnace.jsa"
if [ "${FURNACE_CDS:-1}" != 0 ] && ! printf '%s\n' "${JVM_ARGS[@]}" | grep -q -e SharedArchiveFile -e AutoCreateSharedArchive -e 'Xshare'; then
  JMAJOR=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  if [ -n "$JMAJOR" ] && [ "$JMAJOR" -ge 19 ] 2>/dev/null; then
    if [ -f "$CDS_FILE" ]; then
      MAGIC=$(od -An -tx1 -N4 "$CDS_FILE" 2>/dev/null | tr -d ' ')
      if [ "$MAGIC" != "a8ab0bf0" ] || [ -n "$(find "$APP/lib" -newer "$CDS_FILE" -print -quit 2>/dev/null)" ]; then
        rm -f "$CDS_FILE" 2>/dev/null
      fi
    fi
    if [ -f "$CDS_FILE" ] || [ -w "$APP" ]; then
      CDS_ARGS=("-XX:+AutoCreateSharedArchive" "-XX:SharedArchiveFile=$CDS_FILE")
    fi
  fi
fi
exec java "${CDS_ARGS[@]}" "${JVM_ARGS[@]}" -cp "$APP/lib/*" org.domi.furnace.server.FurnaceMain "$@"
