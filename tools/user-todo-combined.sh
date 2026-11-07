#!/usr/bin/env bash
# docs/user-todo.md 와 docs/user-todo/entries/*.md 를 한 문서로 합쳐 표준 출력에 낸다(저장소 파일은 건드리지 않는다).
# 항목은 파일 이름(날짜-묶음 id)이 큰 것, 즉 최신 순으로 첫 `---` 줄 앞에 끼워 넣는다.
#   tools/user-todo-combined.sh > /tmp/user-todo-all.md
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
main="$root/docs/user-todo.md"
dir="$root/docs/user-todo/entries"
shopt -s nullglob
files=("$dir"/*.md)
entries=""
if ((${#files[@]})); then
  while IFS= read -r f; do
    entries+="$(cat "$f")"$'\n\n'
  done < <(printf '%s\n' "${files[@]}" | LC_ALL=C sort -r)
fi
awk -v entries="$entries" '
  !done && $0 == "---" { printf "%s", entries; done = 1 }
  { print }
  END { if (!done) printf "\n%s", entries }
' "$main"
