#!/usr/bin/env bash
# T-227: 서명표 세 개(바닐라·paper-api·Mojang 이름 Paper 서버)를 개발자 캐시(~/.cache/furnace-vanilla-extract/<판>/)에 만든다.
# 표는 커밋·배포하지 않는다(Mojang 대응표 조건). Paper 서버 jar 는 worldgen-oracle(paperweight)이 닿는 곳에서만 만들어지며,
# 닿지 않으면(-PskipOracle 환경) 그 칸만 "Paper 표 없음"으로 건너뛴다. 사용법: tools/compat/sig-tables.sh [판=1.21.1]
set -euo pipefail
MC="${1:-1.21.1}"
cd "$(dirname "$0")/../.."
G=(./gradlew -q)
"${G[@]}" -PskipOracle=true :vanilla-extract:run -Pmc="$MC" -Pmode=sig-table
"${G[@]}" -PskipOracle=true :vanilla-extract:run -Pmc="$MC" -Pmode=sig-table -PsigPaperApi=true
if line=$("${G[@]}" :worldgen-oracle:printPaperServerJar 2>/dev/null | grep '^PAPER_SERVER_JAR=') ; then
  "${G[@]}" -PskipOracle=true :vanilla-extract:run -Pmc="$MC" -Pmode=sig-table -PsigJar="${line#PAPER_SERVER_JAR=}" -PsigName=paper-server -PsigPrefix=net/minecraft/,org/bukkit/,io/papermc/,com/destroystokyo/
else
  echo "[서명표] Paper 서버 jar 를 못 구함(Paper 저장소에 안 닿음) — Paper 표 없음, 바닐라·paper-api 표만 만들었다"
fi
