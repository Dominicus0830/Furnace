#!/usr/bin/env bash
# docs/user-todo.md 와 docs/user-todo/entries/*.md 를 한 문서로 합쳐 표준 출력에 낸다(저장소 파일은 건드리지 않는다).
# 항목은 파일 이름(날짜-묶음 id)이 큰 것, 즉 최신 순으로 첫 `---` 줄 앞에 끼워 넣는다(없으면 맨 끝).
#   tools/user-todo-combined.sh > /tmp/user-todo-all.md
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
main="$root/docs/user-todo.md"
dir="$root/docs/user-todo/entries"
shopt -s nullglob
files=("$dir"/*.md)
sorted=()
if ((${#files[@]})); then
  while IFS= read -r f; do sorted+=("$f"); done < <(printf '%s\n' "${files[@]}" | LC_ALL=C sort -r)
fi
emit_entries() {
  for f in "${sorted[@]+"${sorted[@]}"}"; do
    cat "$f"
    printf '\n'
  done
}
cut_line="$(grep -n -m1 -x -- '---' "$main" | cut -d: -f1 || true)"
if [[ -n "$cut_line" ]]; then
  head -n $((cut_line - 1)) "$main"
  emit_entries
  tail -n +"$cut_line" "$main"
else
  cat "$main"
  printf '\n'
  emit_entries
fi
