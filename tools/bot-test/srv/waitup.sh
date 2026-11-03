#!/bin/bash
# 사용: waitup.sh <서버 폴더> — "Done (" 이 찍힐 때까지 기다린다
for i in $(seq 1 240); do
  grep -q "Done (" "$1/server.log" 2>/dev/null && exit 0
  pgrep -f "^java -Xmx3G" >/dev/null || { echo "서버 종료됨"; tail -30 "$1/server.log"; exit 1; }
  sleep 1
done; echo timeout; exit 1
