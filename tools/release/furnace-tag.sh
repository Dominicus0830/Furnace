#!/usr/bin/env bash
# Furnace 태그 도우미 — 규칙은 docs/ops/version-tags.md.
#   furnace-tag.sh check <태그>                    이름 형식 검사(맞으면 종류를 출력)
#   furnace-tag.sh next-base [<마크판>]            오늘(UTC)의 다음 base 태그 이름 출력(원격 태그 기준)
#   furnace-tag.sh create <태그> [<커밋>] [-m 설명] [--push]   주석 태그 만들기(기본 HEAD)
#   furnace-tag.sh --selftest                      이름 검사 자체 시험
# 마크판 자리는 숫자와 점(1.21.1, 26.1).
set -euo pipefail

BASE_BRANCH="claude/quirky-wright-lra834"   # 기준 브랜치가 바뀌면 ci.yml 과 함께 바꾼다
MC='[0-9]+(\.[0-9]+)+'

kind_of() {
  local t="$1"
  if   [[ $t =~ ^${MC}$ ]];                                      then echo version
  elif [[ $t =~ ^ms/${MC}/[a-z0-9]+(-[a-z0-9]+)*$ ]];           then echo milestone
  elif [[ $t =~ ^base/${MC}/[0-9]{8}-[0-9]+$ ]];                then echo base
  else return 1; fi
}

mc_of() { local t="${1#ms/}"; t="${t#base/}"; echo "${t%%/*}"; }

selftest() {
  local ok=0 bad=0
  chk() { local want="$1" t="$2" got; got=$(kind_of "$t" 2>/dev/null || echo none)
          if [[ $got == "$want" ]]; then ok=$((ok+1)); else bad=$((bad+1)); echo "FAIL: $t -> $got (기대 $want)"; fi; }
  chk version 1.21.1;  chk version 26.1;  chk version 1.21.11
  chk milestone ms/1.21.1/t141-api; chk milestone ms/1.21.1/n2b-fawe
  chk base base/1.21.1/20261116-1
  chk none v1.21.1;  chk none 1.21.1-0.1.0;  chk none 1;  chk none ms/1.21.1/T141;  chk none ms/1.21.1/a--b
  chk none base/1.21.1/2026-11-16-1; chk none port/1.21.2; chk none foo
  [[ $(mc_of 26.1) == 26.1 && $(mc_of ms/1.21.1/x) == 1.21.1 && $(mc_of base/1.21.1/20261116-2) == 1.21.1 ]] \
    && ok=$((ok+1)) || { bad=$((bad+1)); echo "FAIL: mc_of"; }
  echo "ok=$ok bad=$bad"; [[ $bad -eq 0 ]]
}

cmd="${1:-}"; shift || true
case "$cmd" in
  --selftest) selftest ;;
  check)
    k=$(kind_of "${1:?태그 이름}") || { echo "형식 오류: $1 (docs/ops/version-tags.md 참고)" >&2; exit 1; }
    echo "$k" ;;
  next-base)
    mc="${1:-1.21.1}"; d=$(date -u +%Y%m%d)
    n=$(git ls-remote --tags origin "refs/tags/base/$mc/$d-*" 2>/dev/null | sed -E 's#.*-([0-9]+)(\^\{\})?$#\1#' | sort -n | tail -1 || true)
    echo "base/$mc/$d-$(( ${n:-0} + 1 ))" ;;
  create)
    tag="${1:?태그 이름}"; shift
    commit=HEAD; msg=""; push=0
    while [[ $# -gt 0 ]]; do
      case "$1" in -m) msg="$2"; shift 2 ;; --push) push=1; shift ;; *) commit="$1"; shift ;; esac
    done
    kind=$(kind_of "$tag") || { echo "형식 오류: $tag" >&2; exit 1; }
    mc=$(mc_of "$tag")
    sha=$(git rev-parse --verify "$commit^{commit}")
    # 찍을 수 있는 곳: 기준 브랜치, ver/<마크판>, (판 태그만) main — 원격에 있는 커밋이어야 한다
    ok=0
    for br in "origin/$BASE_BRANCH" "origin/ver/$mc" $([[ $kind == version ]] && echo origin/main); do
      if git rev-parse --verify -q "$br" >/dev/null && git merge-base --is-ancestor "$sha" "$br"; then ok=1; break; fi
    done
    [[ $ok -eq 1 ]] || { echo "거부: $sha 는 origin/$BASE_BRANCH, origin/ver/$mc$([[ $kind == version ]] && echo ', origin/main') 어디에도 없다(git fetch 후 다시)." >&2; exit 1; }
    git rev-parse -q --verify "refs/tags/$tag" >/dev/null && { echo "거부: 태그가 이미 있다: $tag (태그는 옮기지 않는다)" >&2; exit 1; }
    git tag -a "$tag" "$sha" -m "${msg:-$tag}"
    echo "만듦: $tag -> $sha"
    [[ $push -eq 1 ]] && git push origin "refs/tags/$tag" ;;
  *) sed -n 2,8p "$0"; exit 2 ;;
esac
