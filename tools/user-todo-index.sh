#!/usr/bin/env bash
# docs/user-todo/entries/*.md 를 파일당 한 줄로 요약해 낸다(토큰 절약: 합본 300KB 대신 이 목록을 먼저 본다).
# 줄 형식: <파일 이름>  <바이트>  결정 <**결정:** 개수>  <첫 줄 제목>
# 사용: tools/user-todo-index.sh [N]   (N = 최신 N개만, 기본 전부)   필요한 파일만 이어서 cat.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dir="$root/docs/user-todo/entries"
n="${1:-0}"
shopt -s nullglob
files=("$dir"/*.md)
((${#files[@]})) || { echo "항목 파일 없음"; exit 0; }
list=$(printf '%s\n' "${files[@]}" | LC_ALL=C sort -r)
[ "$n" -gt 0 ] && list=$(printf '%s\n' "$list" | head -n "$n")
while IFS= read -r f; do
  d=$(grep -c '\*\*결정:\*\*' "$f" || true)
  t=$(head -n 1 "$f" | sed 's/^# *//' | cut -c1-90)
  printf '%s  %sB  결정 %s  %s\n' "$(basename "$f")" "$(wc -c < "$f" | tr -d ' ')" "$d" "$t"
done <<< "$list"
