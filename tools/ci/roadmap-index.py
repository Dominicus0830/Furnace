#!/usr/bin/env python3
"""docs/roadmap/optimization-tasks.md 의 TASK 표 행을 한 줄로 줄여 낸다(토큰 절약: 91KB 문서 대신 이 목록을 먼저 본다).

줄 형식: <ID> | <우선·크기 칸처럼 짧은 칸들> | <내용 앞 55자> | <상태 낱말이 든 칸>
사용: python3 tools/ci/roadmap-index.py [ID 접두어, 예: T-2 또는 F]   (접두어 없으면 전부)
자세한 내용은 `grep -n "<ID>" docs/roadmap/optimization-tasks.md` 로 해당 행만 읽는다.
상태 칸이 없는 표도 있다 — 낡았을 수 있으니 진행 여부는 열린 PR·git log 로 확인한다.
"""
import re
import signal
import sys
from pathlib import Path

doc = Path(__file__).resolve().parents[2] / "docs" / "roadmap" / "optimization-tasks.md"
signal.signal(signal.SIGPIPE, signal.SIG_DFL)
prefix = sys.argv[1] if len(sys.argv) > 1 else ""
row_id = re.compile(r"^[A-Za-z]+(-[A-Za-z0-9]+)*\d[a-z]?$")
status = re.compile(r"(병합|진행|보류|기각|측정 먼저|채택|대기|끝)")
for line in doc.read_text(encoding="utf-8").splitlines():
    if not line.startswith("|"):
        continue
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    if len(cells) < 3 or not row_id.match(cells[0].replace("**", "")) or not cells[0].replace("**", "").startswith(prefix):
        continue
    rid = cells[0].replace("**", "")
    short = [c for c in cells[1:] if len(c) <= 12]
    longest = max(cells[1:], key=len)
    st = next((c[:30] for c in reversed(cells[1:]) if status.search(c) and len(c) <= 30), "")
    print(f"{rid} | {' '.join(short[:2])} | {longest[:55]} | {st}")
