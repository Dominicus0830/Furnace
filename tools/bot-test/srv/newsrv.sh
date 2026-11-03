#!/bin/bash
# 사용: newsrv.sh <이름> [추가 server.properties 줄...] — $BOT_ROOT/run/<이름> 에 새 월드(평지·오프라인) 서버 폴더를 만든다.
# 바닐라 데이터는 $VANILLA_DATA(data/ 와 version.json 이 든 폴더)를 cache/vanilla-data-1.21.1 로 연결한다(README 참고).
: "${BOT_ROOT:?BOT_ROOT 를 지정하세요}"; : "${VANILLA_DATA:?VANILLA_DATA 를 지정하세요}"
D=$BOT_ROOT/run/$1; shift
rm -rf "$D"; mkdir -p "$D/cache"; ln -s "$VANILLA_DATA" "$D/cache/vanilla-data-1.21.1"
{ echo "online-mode=false"; echo "level-type=minecraft\\:flat"; echo "spawn-protection=0"; echo "view-distance=6"; echo "simulation-distance=6"; for l in "$@"; do echo "$l"; done; } > "$D/server.properties"
echo "$D"
