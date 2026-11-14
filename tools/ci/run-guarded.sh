#!/usr/bin/env bash
# CI 시험 단계 보호막(T-160). 명령(gradle 시험)을 돌리면서:
#   1) 메모리가 모자라면 러너 프로그램 대신 이 명령(gradle 데몬·시험 JVM)이 먼저 OOM 으로 죽게 한다(oom_score_adj=1000, 자식이 물려받는다).
#      러너가 죽으면 단계 제한시간도, 보고서 올리기(always())도 돌지 않고 로그도 남지 않는다 — PR #362 의 조각 2 가 35분 작업 제한에
#      걸려 40분에 로그 없이 취소된 모양과 같다(단계 제한 20분이 발동하지 않은 것은 러너가 응답하지 않았다는 뜻).
#   2) 메모리·디스크·부하를 주기적으로 기록한다($RESOURCE_LOG, 2분마다 한 줄은 콘솔에도) — 다음에 같은 일이 생기면 원인을 본다.
#   3) 러너의 단계 제한보다 먼저 스스로 제한시간을 건다. 넘기면 시험 JVM·gradle 데몬의 스레드 덤프를 남기고 끝낸다(종료 코드 124).
#      러너가 단계를 끊으면 스레드 덤프 없이 끝나 어느 시험이 멈췄는지 알 수 없다.
#
# 사용: tools/ci/run-guarded.sh <제한(분)> -- <명령...>
# 환경 변수: LIMIT_SECONDS(제한을 초로, 스크립트 시험용), RESOURCE_LOG(기본 /tmp/ci-resources.log), DUMP_DIR(기본 /tmp/ci-thread-dumps), SAMPLE_SECONDS(기본 30)
set -uo pipefail

limit_min="${1:?제한(분)이 필요하다}"
shift
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || { echo "명령이 없다" >&2; exit 2; }

res_log="${RESOURCE_LOG:-/tmp/ci-resources.log}"
dump_dir="${DUMP_DIR:-/tmp/ci-thread-dumps}"
sample="${SAMPLE_SECONDS:-30}"
mkdir -p "$dump_dir"
: > "$res_log"

# 1) OOM 때 러너보다 먼저 죽을 쪽 — 올리는 것은 권한 없이 된다(내리는 것만 root 가 필요하다).
echo 1000 > /proc/self/oom_score_adj 2>/dev/null || echo "[보호막] oom_score_adj 를 바꾸지 못했다(계속한다)"

snapshot() {
  local mem swap load disk top
  mem=$(free -m | awk '/^Mem:/ {printf "mem used=%sM avail=%sM", $3, $7}')
  swap=$(free -m | awk '/^Swap:/ {printf "swap used=%sM/%sM", $3, $2}')
  load=$(cut -d' ' -f1-3 /proc/loadavg)
  # 디스크가 차도 러너가 단계 로그를 못 써서 멈출 수 있다 — 작업 폴더와 /tmp 의 남은 칸
  disk=$(df -Pm "$PWD" /tmp 2>/dev/null | awk 'NR>1 {printf "%s%sM", (NR>2?",":""), $4}')
  # RSS 큰 java 프로세스 3개(MB): 시험 JVM=test, gradle 데몬=daemon, 그 밖=java
  top=$(ps -eo rss=,args= --sort=-rss 2>/dev/null | awk '/java/ {k="java"; if ($0 ~ /GradleWorkerMain/) k="test"; else if ($0 ~ /GradleDaemon/) k="daemon";
        printf "%s=%dM ", k, $1/1024; if (++c==3) exit}')
  echo "$(date -u +%H:%M:%S) $mem $swap disk-free=$disk load=$load java=[${top% }]"
}

# 이 명령의 자손 프로세스(gradle wrapper → 데몬 → 시험 JVM). 다른 작업의 java 는 건드리지 않는다.
descendants() {
  local kids k
  kids=$(ps -o pid= --ppid "$1" 2>/dev/null)
  for k in $kids; do
    echo "$k"
    descendants "$k"
  done
}

dump_threads() {
  local why="$1" pid main f
  echo "[보호막] ${why} — 스레드 덤프를 ${dump_dir} 에 남긴다"
  for pid in "$cmd" $(descendants "$cmd"); do
    [ "$(ps -o comm= -p "$pid" 2>/dev/null)" = java ] || continue
    main=$(ps -o args= -p "$pid" 2>/dev/null)
    f="$dump_dir/threads-${pid}.txt"
    { echo "# pid=$pid ($why)"; echo "# $main" | cut -c1-300; jcmd "$pid" Thread.print -l 2>&1; } > "$f" || true
    # 콘솔에는 시험 스레드가 어디서 멈췄는지 볼 만큼만(전체는 산출물)
    if [[ "$main" == *GradleWorkerMain* ]]; then
      echo "----- 시험 JVM $pid 스레드 덤프(시험 스레드만, 전체는 산출물) -----"
      awk '/^"(Test worker|junit)/,/^$/' "$f" | head -200
    fi
  done
}

"$@" &
cmd=$!

(
  i=0
  while kill -0 "$cmd" 2>/dev/null; do
    line=$(snapshot)
    echo "$line" >> "$res_log"
    # 2분마다 한 줄은 콘솔(=단계 로그)에도
    if [ $((i % (120 / sample))) -eq 0 ]; then echo "[자원] $line"; fi
    i=$((i + 1))
    sleep "$sample"
  done
) &
sampler=$!

deadline=$(( $(date +%s) + ${LIMIT_SECONDS:-$((limit_min * 60))} ))
timed_out=0
while kill -0 "$cmd" 2>/dev/null; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    timed_out=1
    break
  fi
  sleep 5
done

if [ "$timed_out" = 1 ]; then
  echo "::error title=시험 단계 제한시간::${limit_min}분 안에 끝나지 않았습니다. 스레드 덤프·자원 기록을 남기고 멈춥니다."
  snapshot | tee -a "$res_log"
  dump_threads "제한시간 ${limit_min}분 초과"
  # 명령과 그 자손(gradle 데몬·시험 JVM)을 끈다
  procs=$(descendants "$cmd")
  kill "$cmd" $procs 2>/dev/null
  sleep 5
  kill -9 "$cmd" $procs 2>/dev/null
  wait "$cmd" 2>/dev/null
  kill "$sampler" 2>/dev/null
  exit 124
fi

wait "$cmd"
rc=$?
kill "$sampler" 2>/dev/null
wait "$sampler" 2>/dev/null
peak=$(awk '{for (i=1;i<=NF;i++) if ($i ~ /^avail=/) {v=substr($i,7)+0; if (min=="" || v<min) min=v}} END {print min}' "$res_log")
echo "[자원] 끝: 종료 코드 $rc, 가장 적었던 남은 메모리 ${peak:-?}M(기록 $res_log)"
# 시험 JVM 이 SIGKILL(137)로 끝났다면 gradle 이 "finished with non-zero exit value 137" 을 찍는다 — 위 메모리 기록과 함께 본다.
exit "$rc"
