#!/bin/bash
# 블록 동작 차분 비교: 같은 시나리오를 순정 1.21.1 서버와 Furnace 에 돌리고 저장된 블록을 비교한다.
# 사용: run.sh <시나리오 파일> <영역 x1,y1,z1,x2,y2,z2> <작업 폴더>
#   VANILLA_JAR  순정 서버 jar(기본 tools/vanilla-data/minecraft_server.1.21.1.jar — 커밋하지 않는다)
# 결과: <작업 폴더>/vanilla.txt, furnace.txt, diff.txt (diff 가 비면 일치)
set -u
REPO=$(cd "$(dirname "$0")/../.." && pwd)
SCN=$(realpath "$1"); AREA=$2; W=$(realpath -m "$3")
VANILLA_JAR=${VANILLA_JAR:-$REPO/tools/vanilla-data/minecraft_server.1.21.1.jar}
FURNACE_BIN=$REPO/furnace-server/build/install/furnace-server/bin/furnace-server
NAME=${ORACLE_NAME:-Oracle}
UUID=$(python3 -c "import hashlib,uuid,sys;h=bytearray(hashlib.md5(b'OfflinePlayer:$NAME').digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))")

prepare() {   # <폴더> <포트>
    rm -rf "$1"; mkdir -p "$1"
    cat > "$1/server.properties" <<P
online-mode=false
level-type=minecraft\:flat
generate-structures=false
level-seed=1
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-protection=0
network-compression-threshold=-1
server-port=$2
max-players=4
enable-command-block=false
view-distance=4
simulation-distance=4
P
    echo "eula=true" > "$1/eula.txt"
    echo "[{\"uuid\":\"$UUID\",\"name\":\"$NAME\",\"level\":4,\"bypassesPlayerLimit\":false}]" > "$1/ops.json"
}

run_server() {   # <폴더> <명령…>  — FIFO 로 콘솔을 잡고 Done 까지 기다린다
    local dir=$1; shift
    rm -f "$dir/stdin.fifo"; mkfifo "$dir/stdin.fifo"
    ( cd "$dir" && exec "$@" < stdin.fifo > out.log 2>&1 ) &
    eval "PID_$(basename "$dir")=$!"
    exec {FD}>"$dir/stdin.fifo"
    for i in $(seq 1 300); do grep -q 'Done (' "$dir/out.log" 2>/dev/null && break; sleep 1; done
    eval "FD_$(basename "$dir")=$FD"
}

stop_server() {   # <폴더>
    local fdvar="FD_$(basename "$1")"; local fd=${!fdvar}
    local pidvar="PID_$(basename "$1")"; local pid=${!pidvar}
    echo "stop" >&"$fd"; exec {fd}>&-
    for i in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
}

bot() {   # <포트>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*ScenarioBot*' \
        -Dfurnace.scn.port=$1 -Dfurnace.scn.script="$SCN" -Dfurnace.scn.name=$NAME --rerun-tasks >/dev/null 2>&1) || echo "봇 실패(포트 $1)"
}

dump() {   # <월드> <출력>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*WorldDump*' \
        -Dfurnace.dump.world="$1" -Dfurnace.dump.area="$AREA" -Dfurnace.dump.out="$2" --rerun-tasks >/dev/null 2>&1) || echo "덤프 실패: $1"
}

mkdir -p "$W"
(cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:installDist) || exit 1
prepare "$W/vanilla" 25601
prepare "$W/furnace" 25602
run_server "$W/vanilla" java -Xmx2G -jar "$VANILLA_JAR" nogui
bot 25601
stop_server "$W/vanilla"
run_server "$W/furnace" env JAVA_OPTS="-Xmx3G -Dfurnace.vanillaJar=$VANILLA_JAR" "$FURNACE_BIN"
bot 25602
stop_server "$W/furnace"
dump "$W/vanilla/world" "$W/vanilla.txt"
dump "$W/furnace/world" "$W/furnace.txt"
diff "$W/vanilla.txt" "$W/furnace.txt" > "$W/diff.txt"
if [ -s "$W/diff.txt" ]; then echo "차이 $(grep -c '^<' "$W/diff.txt")칸 — $W/diff.txt"; else echo "일치"; fi
