#!/bin/bash
# 사용: run.sh <서버 jar> <서버 폴더> [jvm 옵션...] — 표준 입력은 <서버 폴더>/cmds 파일(줄을 덧붙이면 콘솔 명령)
JAR=$(readlink -f "$1"); DIR=$2; shift 2
mkdir -p "$DIR"; cd "$DIR" || exit 1
[ -f eula.txt ] || echo "eula=true" > eula.txt
: > cmds
nohup bash -c "tail -n0 -f cmds | exec java -Xmx3G $* -jar '$JAR' --nogui" > server.log 2>&1 &
