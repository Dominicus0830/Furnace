#!/usr/bin/env python3
"""Paper 두 판의 패치 묶음을 견준다(읽기 전용 분석). 패치는 번호 접두(`0123-이름.patch`)가 판마다 밀리므로 이름으로 맞춘다.

입력: Paper 저장소의 두 체크아웃 폴더(각각 `patches/api`·`patches/server`·`patches/unapplied` 가 있는 판).
  git init X && git -C X remote add origin https://github.com/PaperMC/Paper && git -C X fetch --depth=1 origin <커밋> && git -C X checkout FETCH_HEAD
  (1.21.1 = 33ff3dff217bfa11a41d157c88d903b3965ac975, 1.21.3 정식 직후 = f84d99ab822a037159378ad9cdf9a924db8e1904 — 1.21.2 용 Paper 정식 판은 없다)
사용법: tools/compat/paper-patch-diff.py <옛 폴더> <새 폴더> [--out 보고서.md] [--tsv 목록.tsv]

패치는 Paper 소스(GPLv3)다. 이 도구는 동작 비교용으로 읽기만 하고 내용을 옮기지 않는다. 보고서의 패치 이름은 Paper 쪽 이름이며 Mojang 이름이 아니다.
"""
import argparse
import collections
import difflib
import os
import re

NUM = re.compile(r"^\d{4}-")


def load(root):
    res = {}
    for sub in ("api", "server", "unapplied"):
        d = os.path.join(root, "patches", sub)
        if sub == "unapplied":
            # unapplied 는 api/server 하위 폴더를 가질 수 있다
            pass
        for dp, _, fs in os.walk(d):
            for f in fs:
                if f.endswith(".patch"):
                    rel = os.path.relpath(os.path.join(dp, f), os.path.join(root, "patches")).replace(os.sep, "/")
                    kind = rel.split("/")[0] if sub != "unapplied" else "unapplied"
                    name = NUM.sub("", os.path.basename(rel))
                    with open(os.path.join(dp, f), encoding="utf-8", errors="replace") as fh:
                        res[(kind, name)] = fh.read()
    return res


def body_lines(text):
    """From/Date/번호 같은 머리를 뺀 변경 줄만(+/-)."""
    return [l for l in text.splitlines() if (l.startswith("+") or l.startswith("-")) and not l.startswith(("+++", "---"))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--out")
    ap.add_argument("--tsv")
    o = ap.parse_args()
    a, b = load(o.old), load(o.new)
    kinds = sorted({k for k, _ in a} | {k for k, _ in b})
    rows = []
    stat = {k: collections.Counter() for k in kinds}
    for key in sorted(set(a) | set(b)):
        k, n = key
        if key not in a:
            stat[k]["added"] += 1
            rows.append((k, "추가", n, len(body_lines(b[key])), 0))
        elif key not in b:
            stat[k]["removed"] += 1
            rows.append((k, "삭제", n, 0, len(body_lines(a[key]))))
        else:
            la, lb = body_lines(a[key]), body_lines(b[key])
            if la == lb:
                stat[k]["same"] += 1
                continue
            sm = difflib.SequenceMatcher(None, la, lb, autojunk=False)
            ins = dele = 0
            for tag, i1, i2, j1, j2 in sm.get_opcodes():
                if tag in ("replace", "delete"):
                    dele += i2 - i1
                if tag in ("replace", "insert"):
                    ins += j2 - j1
            tot = max(len(la), len(lb), 1)
            small = (ins + dele) <= max(6, tot // 10)
            stat[k]["changed_small" if small else "changed_big"] += 1
            rows.append((k, "변경(작음)" if small else "변경(큼)", n, ins, dele))
    # 이름이 바뀐 것 후보: 삭제·추가 중 같은 변경 줄을 가진 것
    rem = {n: (k, body_lines(a[(k, n)])) for (k, n) in a if (k, n) not in b}
    add = {n: (k, body_lines(b[(k, n)])) for (k, n) in b if (k, n) not in a}
    ren = 0
    byb = collections.defaultdict(list)
    for n, (k, ls) in add.items():
        byb[hash(tuple(ls))].append(n)
    for n, (k, ls) in rem.items():
        if ls and hash(tuple(ls)) in byb:
            ren += 1
    lines = [f"# Paper 패치 차이 ({os.path.basename(os.path.abspath(o.old))} → {os.path.basename(os.path.abspath(o.new))})\n"]
    lines.append("| 묶음 | 옛 패치 | 새 패치 | 같음 | 변경(작음) | 변경(큼) | 추가 | 삭제 |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for k in kinds:
        s = stat[k]
        na = sum(1 for (kk, _) in a if kk == k)
        nb = sum(1 for (kk, _) in b if kk == k)
        lines.append(f"| {k} | {na} | {nb} | {s['same']} | {s['changed_small']} | {s['changed_big']} | {s['added']} | {s['removed']} |")
    lines.append("")
    lines.append(f"- 이름만 바뀐 것으로 보이는 삭제·추가 쌍(변경 줄이 같음): {ren}")
    lines.append("- 변경(작음): 변경 줄이 전체의 10% 이하(또는 6줄 이하) — 주로 줄 번호·문맥 갱신. 변경(큼): 패치 내용 자체가 바뀜(새 판 이식 때 손본 곳).")
    lines.append("")
    lines.append("## 큰 변경·추가·삭제 목록 (변경 줄 수 큰 순 상위 60)\n")
    for k, kind, n, ins, dele in sorted((r for r in rows if r[1] != "변경(작음)"), key=lambda r: -(r[3] + r[4]))[:60]:
        lines.append(f"- [{k}] {kind} {n} (+{ins}/-{dele})")
    text = "\n".join(lines) + "\n"
    if o.out:
        with open(o.out, "w", encoding="utf-8") as f:
            f.write(text)
    if o.tsv:
        with open(o.tsv, "w", encoding="utf-8") as f:
            f.write("묶음\t종류\t패치 이름\t추가 줄\t삭제 줄\n")
            for r in rows:
                f.write("\t".join(map(str, r)) + "\n")
    print(text)


if __name__ == "__main__":
    main()
