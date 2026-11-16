#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# 두 클래스 폴더(전·후)의 바이트코드가 "명령까지 같은지" 견준다 — 버전 상수 생성(V-01) 같은 "결과가 바뀌지 않는 정리" 증명용.
#   tools/ci/javap-same.sh <전 폴더> <후 폴더> [--allow-new 패턴]
# 클래스마다 `javap -v -p -c` 를 뜬 뒤 javap-norm.py 로 상수 풀 크기에서 오는 차이(번호·표·ldc_w·명령 위치)만 지운다.
# 남는 것 = 필드 서술자와 ConstantValue, 메서드 명령(ldc 의 값 주석 포함), BootstrapMethods(문자열 이어 붙이기 재료 포함).
# 그러므로 "손으로 쓴 숫자"가 "생성 상수 참조"로 바뀌어도 javac 가 같은 값을 박았다면 통과하고,
# 실행 중 getstatic/호출이 새로 생기거나 값이 달라지면 그 클래스만 diff 로 보인다.
# 후 폴더에만 있는 클래스(생성된 GameVersion 등)는 --allow-new 정규식에 맞을 때만 허용한다. 종료 코드: 0 같음, 1 다름.
set -euo pipefail
before=${1:?전 폴더}; after=${2:?후 폴더}; shift 2
allow_new='^$'
if [[ ${1:-} == --allow-new ]]; then allow_new=${2:?정규식}; fi

# 클래스 하나씩 javap 를 띄우면 JVM 시작이 6천 번이라 느리다 → 폴더 전체를 한 번에 뜨고(xargs 묶음) 클래스별 파일로 나눈다.
dump() {   # dump <폴더> <출력 폴더>
  local dir=$1 out=$2; mkdir -p "$out"
  ( cd "$dir" && find . -name '*.class' | sort > "$out/.list" )
  ( cd "$dir" && xargs -a "$out/.list" -n 1500 javap -v -p -c 2>/dev/null ) \
    | python3 "$(dirname "${BASH_SOURCE[0]}")/javap-norm.py" "$out" "$(cd "$dir" && pwd)/"
}

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
dump "$before" "$tmp/b"; dump "$after" "$tmp/a"
sed 's#^\./##' "$tmp/b/.list" | sort > "$tmp/b.txt"
sed 's#^\./##' "$tmp/a/.list" | sort > "$tmp/a.txt"

comm -23 "$tmp/b.txt" "$tmp/a.txt" | sed 's/^/없어짐: /' > "$tmp/gone.txt"
comm -13 "$tmp/b.txt" "$tmp/a.txt" | grep -Ev "$allow_new" | sed 's/^/새 클래스: /' > "$tmp/new.txt" || true
comm -12 "$tmp/b.txt" "$tmp/a.txt" > "$tmp/both.txt"

: > "$tmp/diff.txt"
while read -r f; do
  k=${f//\//__}.txt
  cmp -s "$tmp/b/$k" "$tmp/a/$k" || echo "$f" >> "$tmp/diff.txt"
done < "$tmp/both.txt"

n=$(wc -l < "$tmp/both.txt")
if [[ -s $tmp/gone.txt || -s $tmp/new.txt || -s $tmp/diff.txt ]]; then
  cat "$tmp/gone.txt" "$tmp/new.txt"
  while read -r f; do echo "다름: $f"; done < "$tmp/diff.txt"
  echo "javap-same: 다름 ($(wc -l < "$tmp/diff.txt") / $n 클래스)"; exit 1
fi
echo "javap-same: 같음 ($n 클래스, 허용된 새 클래스 $(comm -13 "$tmp/b.txt" "$tmp/a.txt" | wc -l)개)"
