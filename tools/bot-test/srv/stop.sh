#!/bin/bash
# 사용: stop.sh <서버 폴더> — stop 을 보내고 최대 90초 기다린다
echo stop >> "$1/cmds"
for i in $(seq 1 90); do pgrep -f "^java -Xmx3G" >/dev/null || break; sleep 1; done
if pgrep -f "^java -Xmx3G" >/dev/null; then echo "강제 종료"; pkill -9 -f "^java -Xmx3G"; fi
pkill -f "^tail -n0 -f cmds" 2>/dev/null; true
