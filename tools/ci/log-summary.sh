#!/usr/bin/env bash
# gradle 시험·빌드 로그를 몇 줄로 줄여 보여 준다(토큰 절약). 로그 전체를 tail/cat 하지 않는다.
# 보여 주는 것: 끝 상태(BUILD/EXIT), 실패한 시험, 컴파일 오류, 시험 개수 줄, 마지막 몇 줄.
# 사용: tools/ci/log-summary.sh <로그 파일> [마지막에 보일 줄 수(기본 8)] [항목별 최대 줄 수(기본 30)]
set -uo pipefail
log="${1:?로그 파일이 필요하다}"
tail_n="${2:-8}"
max="${3:-30}"
[ -f "$log" ] || { echo "로그 없음: $log" >&2; exit 2; }

echo "== $(wc -l < "$log") 줄, $(wc -c < "$log") 바이트: $log"
echo "== 끝 상태"
grep -E '^(BUILD (SUCCESSFUL|FAILED)|EXIT=)' "$log" | tail -3 || true
echo "== 실패한 시험 (FAILED)"
grep -E ' FAILED$' "$log" | sort -u | head -n "$max" || true
echo "== 컴파일·구성 오류 (error:, What went wrong)"
{ grep -E '(error:|\.java:[0-9]+: error)' "$log" | head -n "$max"; grep -A3 'What went wrong' "$log" | head -n 8; } || true
echo "== 시험 개수 줄"
grep -E '[0-9]+ tests? completed|tests completed' "$log" | tail -2 || true
echo "== 마지막 ${tail_n}줄"
tail -n "$tail_n" "$log" | cut -c1-240
