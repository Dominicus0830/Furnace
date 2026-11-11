#!/usr/bin/env python3
# JaCoCo XML(jacoco.xml)을 읽어 패키지별 줄·가지 커버리지 표(마크다운)를 낸다. CI 의 coverage 작업이 실행 요약에 붙이고, 로컬에서도 쓴다.
# 사용: python3 -I tools/ci/coverage-summary.py furnace-server/build/reports/coverage/jacoco.xml [깊이=6] [상위 N=40]
#   깊이: 패키지 이름을 앞에서 몇 단계까지 묶는가. 6 이면 server 아래 두 단계(org.domi.furnace.server.block.types 그대로), 5 이면 한 단계(…server.block)
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

path = sys.argv[1]
depth = int(sys.argv[2]) if len(sys.argv) > 2 else 6
top = int(sys.argv[3]) if len(sys.argv) > 3 else 40

root = ET.parse(path).getroot()
acc = defaultdict(lambda: {"LINE": [0, 0], "BRANCH": [0, 0]})
total = {"LINE": [0, 0], "BRANCH": [0, 0]}
for pkg in root.findall("package"):
    name = pkg.get("name").replace("/", ".")
    key = ".".join(name.split(".")[:depth]) or "(root)"
    for c in pkg.findall("counter"):
        t = c.get("type")
        if t in ("LINE", "BRANCH"):
            cov, mis = int(c.get("covered")), int(c.get("missed"))
            acc[key][t][0] += cov
            acc[key][t][1] += mis
            total[t][0] += cov
            total[t][1] += mis


def pct(cm):
    n = cm[0] + cm[1]
    return "-" if n == 0 else f"{100 * cm[0] / n:.1f}%"


print("| 패키지 | 줄 | 놓친 줄 | 가지 |")
print("|---|---|---|---|")
for key, v in sorted(acc.items(), key=lambda kv: -kv[1]["LINE"][1])[:top]:
    print(f"| `{key}` | {pct(v['LINE'])} | {v['LINE'][1]} | {pct(v['BRANCH'])} |")
print(f"| **전체** | **{pct(total['LINE'])}** | {total['LINE'][1]} | {pct(total['BRANCH'])} |")
