#!/usr/bin/env bash
# 서버 시험 조각이 "시험 실패"로 끝났을 때, 처음에 실패한 시험 클래스만 한 번 다시 돌린다(.github/workflows/ci.yml 의 재시도 단계).
# 부하에서만 가끔 실패하는 시간 민감 시험을 흡수하려는 것이지, 실패를 가리려는 것이 아니다. 그래서 아래 경우는 재시도하지 않고 바로 실패로 끝낸다:
#   1) 시험 실패가 아니라 시험 JVM 이상 종료·빌드 오류(일부 시험이 아예 안 돈 채 초록이 되는 것을 막는다)
#   2) 시험 기본 제한시간 초과(TimeoutException) — 멈춘 시험이다. 다시 돌려도 같은 시간만 더 쓴다
#   3) @NoRetry 가 붙은 시험의 실패(확률 실패를 잡으려는 반복 시험은 재시도로 가리면 의미가 없다 — NoRetryFailureListener)
# 재시도에서 통과한 시험은 "흔들린 시험" 주석(::warning)·실행 요약·flaky-tests.txt 로 남긴다.
#
# 사용: tools/ci/retry-failed-tests.sh <조각 번호>
# 환경 변수(시험용 재정의): SHARD_LOG, TEST_RESULTS_DIR, NO_RETRY_REPORT, FIRST_RUN_DIR, GRADLE_CMD, REPORTS_DIR, GITHUB_STEP_SUMMARY
set -euo pipefail

shard="${1:?조각 번호가 필요하다}"
log="${SHARD_LOG:-/tmp/shard-run.log}"
dir="${TEST_RESULTS_DIR:-furnace-server/build/test-results/test}"
noretry="${NO_RETRY_REPORT:-furnace-server/build/no-retry-failures.txt}"
out="${FIRST_RUN_DIR:-/tmp/first-run}"
reports="${REPORTS_DIR:-furnace-server/build/reports/tests}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
read -r -a gradle <<< "${GRADLE_CMD:-./gradlew -PskipOracle=true}"

if ! grep -q "There were failing tests" "$log" || grep -q "finished with non-zero exit value" "$log"; then
  echo "::error title=재시도 안 함::시험 실패가 아니라 시험 JVM 이상 종료·빌드 오류·단계 제한시간으로 보여 재시도하지 않습니다."
  exit 1
fi

# 시험 기본 제한시간(junit-platform.properties)을 넘겨 실패한 시험이 있으면 재시도하지 않는다.
if grep -qE "java\.util\.concurrent\.TimeoutException: .* timed out after" "$dir"/TEST-*.xml 2>/dev/null; then
  echo "::error title=재시도 안 함::시험 기본 제한시간을 넘긴 시험이 있습니다(멈춘 시험으로 보고 재시도하지 않습니다):"
  grep -hoE "java\.util\.concurrent\.TimeoutException: [^\"<]* timed out after [^\"<]*" "$dir"/TEST-*.xml | sort -u | sed 's/^/  /'
  exit 1
fi

# @NoRetry 시험이 실패했으면 재시도하지 않는다(재시도가 확률 실패를 가리지 않게).
if [ -s "$noretry" ]; then
  echo "::error title=재시도 안 함::@NoRetry 가 붙은 시험이 실패했습니다(재시도하지 않고 실패로 둡니다):"
  sed 's/^/  /' "$noretry"
  exit 1
fi

failed=$(awk '
  /<testcase /{ match($0,/name="[^"]*"/); n=substr($0,RSTART+6,RLENGTH-7); match($0,/classname="[^"]*"/); c=substr($0,RSTART+11,RLENGTH-12) }
  /<(failure|error)[ >]/{ print c "#" n }
' "$dir"/TEST-*.xml 2>/dev/null | sort -u)
classes=$(printf '%s\n' "$failed" | sed 's/#.*//' | sort -u | sed '/^$/d')
if [ -z "$classes" ]; then
  echo "::error title=재시도 안 함::실패한 시험을 찾지 못했습니다(컴파일·환경 오류로 보임). 재시도하지 않습니다."
  exit 1
fi

echo "::warning::서버 테스트 조각 ${shard} 에서 실패한 시험 클래스만 다시 돌립니다(불안정 시험 의심):"
printf '%s\n' "$failed"
# 첫 실행 결과는 보고서 올리기용으로 따로 보관(재시도가 같은 폴더를 덮어쓴다)
rm -rf "$out" && mkdir -p "$out" && cp -r "$reports" "$out/reports" || true
printf '%s\n' "$failed" > "$out/failed-tests.txt"
args=()
for c in $classes; do args+=(--tests "$c"); done
"${gradle[@]}" :furnace-server:test "${args[@]}"

# 여기까지 왔으면 재시도에서 통과한 것이다 — 처음에 실패했다는 기록을 남긴다(초록 뒤에 가려지지 않게).
printf '%s\n' "$failed" > "$out/flaky-tests.txt"
while IFS= read -r t; do
  [ -n "$t" ] && echo "::warning title=흔들린 시험 (조각 ${shard}/3)::${t} — 처음 실패, 재시도에서 통과"
done <<< "$failed"
{
  echo "### 흔들린 시험 (조각 ${shard}/3: 처음 실패, 재시도에서 통과)"
  printf '%s\n' "$failed" | sed 's/^/- /'
  echo
  echo "두 번 이상 나온 시험은 고칠 대상이다(docs/ci.md). 확률 실패를 잡는 시험은 재시도로 가리지 않도록 @NoRetry 를 붙인다."
} >> "$summary"
