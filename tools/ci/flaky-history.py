#!/usr/bin/env python3
# CI 재시도로 가려진 흔들린 시험을 모아 센다(T-161).
# 서버 시험 조각은 처음 실패한 시험 클래스만 다시 돌리고, 재시도에서 통과한 시험을 "… — 처음 실패, 재시도에서 통과" 주석(::warning)과
# 산출물 flaky-tests-N 으로 남긴다(tools/ci/retry-failed-tests.sh). 실행은 초록이라 아무도 안 본다 — 이 도구가 그 주석을 모아 시험별로 센다.
#
# 사용: python3 tools/ci/flaky-history.py [--repo 소유자/이름] [--logs 폴더]
#   GITHUB_TOKEN 이 있으면 쓴다(없어도 공개 저장소는 시간당 60번까지 읽힌다).
#   --logs 를 주면 그 실행 조각의 로그를 받아 시험마다 처음 실패 메시지 첫 줄을 붙인다(로그 받기는 토큰이 필요할 수 있다).
# 산출물(flaky-tests-N)은 7일 보관이라 그보다 오래된 흔들림은 안 보인다.
import argparse
import json
import os
import re
import sys
import urllib.request
from collections import defaultdict

API = "https://api.github.com"


def get(url, raw=False):
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    tok = os.environ.get("GITHUB_TOKEN")
    if tok:
        req.add_header("Authorization", "Bearer " + tok)
    with urllib.request.urlopen(req) as r:
        data = r.read()
    return data.decode("utf-8", "replace") if raw else json.loads(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="Dominicus0830/Furnace")
    ap.add_argument("--logs", help="실패 메시지를 찾으려고 조각 로그를 받아 둘 폴더")
    a = ap.parse_args()

    runs = set()
    for shard in (1, 2, 3):
        d = get(f"{API}/repos/{a.repo}/actions/artifacts?per_page=100&name=flaky-tests-{shard}")
        for art in d.get("artifacts", []):
            runs.add(art["workflow_run"]["id"])

    seen = defaultdict(list)    # 시험 → [(실행, 조각)]
    first_msg = {}
    for run in sorted(runs):
        jobs = get(f"{API}/repos/{a.repo}/actions/runs/{run}/jobs?per_page=50")["jobs"]
        for j in jobs:
            if not j["name"].startswith("server-tests"):
                continue
            anns = get(f"{API}/repos/{a.repo}/check-runs/{j['id']}/annotations")
            flaky = [x["message"].split(" — ")[0] for x in anns if "재시도에서 통과" in x["message"]]
            for t in flaky:
                seen[t].append((run, j["name"]))
            if flaky and a.logs:
                os.makedirs(a.logs, exist_ok=True)
                try:
                    text = get(f"{API}/repos/{a.repo}/actions/jobs/{j['id']}/logs", raw=True)
                except Exception as e:   # 토큰 없이 막히면 메시지 없이 센다
                    print(f"[로그 못 받음] {j['id']}: {e}", file=sys.stderr)
                    continue
                with open(os.path.join(a.logs, f"{j['id']}.txt"), "w", encoding="utf-8") as f:
                    f.write(text)
                lines = text.split("\n")
                for i, line in enumerate(lines):
                    m = re.search(r"(\w+) > (\w+)\(\) FAILED$", line)
                    if m and i + 1 < len(lines):
                        key = m.group(1) + "#" + m.group(2)
                        first_msg.setdefault(key, lines[i + 1][29:].strip())

    print(f"재시도 산출물이 있는 실행 {len(runs)}개, 흔들린 시험 {sum(len(v) for v in seen.values())}건")
    for t, hits in sorted(seen.items(), key=lambda kv: -len(kv[1])):
        last = max(r for r, _ in hits)
        short = t.rsplit(".", 1)[-1].replace("()", "")
        msg = first_msg.get(short, "")
        print(f"{len(hits):3d}  {t}  (마지막 실행 {last}){'  — ' + msg if msg else ''}")


if __name__ == "__main__":
    main()
