#!/usr/bin/env python3
"""서버 시험 조각(-PtestShard=i/N)이 고르게 나뉘도록 furnace-server/build.gradle.kts 의 shardSalt 를 찾는다.

사용: 서버 시험을 한 번 돌린 뒤(furnace-server/build/test-results/test 에 XML 이 있어야 한다)
  python3 -I tools/ci/shard-balance.py [조각 수=3] [탐색할 salt 수=20000]
조각 배정 규칙은 Gradle 쪽과 같다: Java String.hashCode(클래스 파일 경로(/ 구분, $ 앞까지) + salt) 를 조각 수로 나눈 나머지.
시험 구성이 크게 바뀌어 조각 시간이 한쪽으로 쏠리면 다시 돌려 새 salt 를 넣는다(안 해도 합친 결과는 전체와 같다).
"""
import glob, re, sys

def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= (1 << 31) else h

shards = int(sys.argv[1]) if len(sys.argv) > 1 else 3
tries = int(sys.argv[2]) if len(sys.argv) > 2 else 20000
rows = []
for f in glob.glob('furnace-server/build/test-results/test/TEST-*.xml'):
    head = open(f, encoding='utf-8').read(800)
    m = re.search(r'<testsuite name="([^"]+)".*? time="([\d.]+)"', head)
    if m:
        rows.append((m.group(1).split('$')[0].replace('.', '/'), float(m.group(2))))
if not rows:
    sys.exit('시험 결과 XML 이 없다. 먼저 :furnace-server:test 를 돌린다.')

def sums(salt):
    s = [0.0] * shards
    for name, t in rows:
        s[java_hash(name + salt) % shards] += t   # 파이썬 %는 Math.floorMod 와 같다
    return s

best = min(range(tries), key=lambda i: max(sums(str(i))))
print(f'shardSalt="{best}"  조각별 초={[round(x) for x in sums(str(best))]}  (salt 없음: {[round(x) for x in sums("")]}, 전체 {sum(t for _, t in rows):.0f}s)')
