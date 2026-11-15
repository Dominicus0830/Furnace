#!/usr/bin/env python3
"""서버 시험 조각(-PtestShard=i/N)의 균형을 보고, 배정에 쓰는 시간 파일 furnace-server/test-shard-times.tsv 를 갱신한다(T-162).

배정 규칙(furnace-server/build.gradle.kts 와 같다 — 한쪽을 바꾸면 assign() 도 바꾼다):
  - 시간 파일에 있는 클래스: 오래 걸리는 것부터(같으면 이름순) 지금 가장 가벼운 조각(같으면 번호가 작은 쪽)에 넣는다(LPT).
  - 파일에 없는 클래스(새 시험): Math.floorMod(String.hashCode(클래스 경로 + shardSalt), 조각 수).
  클래스 경로는 '/' 로 구분하고 '$' 앞까지다(@Nested·내부 클래스는 바깥 클래스를 따른다).

사용:
  python3 -I tools/ci/shard-balance.py [표본 ...]           지금 배정이 표본마다 얼마나 고른지 본다
  python3 -I tools/ci/shard-balance.py --write [표본 ...]   표본들로 시간 파일을 새로 쓰고, 새 배정의 균형을 본다
  python3 -I tools/ci/shard-balance.py --export 시간.tsv    로컬 시험 결과 XML 을 클래스별 시간 TSV 로 쓴다(CI 산출물 shard-times-N)

표본 하나 = 서버 시험 전체를 한 번 돈 시간 기록이다. 표본은 다음 중 하나로 준다.
  - 폴더: 안(하위 폴더 포함)의 TEST-*.xml 과 *.tsv 를 모두 합쳐 한 표본으로 본다.
    CI 실행 하나의 산출물 shard-times-1·2·3 을 한 폴더에 풀면 그 실행 전체가 한 표본이다.
  - .tsv 파일 하나(--export 가 쓴 꼴: "클래스 경로<TAB>초").
  표본을 안 주면 furnace-server/build/test-results/test(로컬에서 :furnace-server:test 를 돌린 결과) 하나를 쓴다.
--write 는 표본마다 합을 같게 맞춘 뒤(컴퓨터 빠르기 차이를 지움) 클래스별 평균을 낸다. 여러 실행을 줄수록 흔들림이 준다.

옛 방식(해시 + shardSalt 고르기)을 버린 까닭: 접미사 salt 는 조각 수 3 에서 salt 길이마다 사실상 같은 나눔(이름표만 바뀜)을 내서
0~19999 를 뒤져도 서로 다른 나눔은 다섯 개뿐이었다. 그래서 가장 큰 클래스들이 몰린 조각을 풀 수 없었다(docs/ci.md "조각 균형").
"""
import argparse, glob, os, re, sys

GRADLE = 'furnace-server/build.gradle.kts'
TIMES = 'furnace-server/test-shard-times.tsv'
DEFAULT_XML = 'furnace-server/build/test-results/test'
TEST_SRC = 'furnace-server/src/test/java'
SUITE_RE = re.compile(r'<testsuite name="([^"]+)".*? time="([\d.]+)"')


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= (1 << 31) else h


def class_key(suite_name):
    return suite_name.split('$')[0].replace('.', '/')


def read_xml(path, times):
    with open(path, encoding='utf-8', errors='replace') as f:
        m = SUITE_RE.search(f.read(2000))
    if m:
        k = class_key(m.group(1))
        times[k] = times.get(k, 0.0) + float(m.group(2))


def read_tsv(path, times):
    with open(path, encoding='utf-8') as f:
        for line in f:
            if not line.strip() or line.startswith('#'):
                continue
            name, sec = line.rstrip('\n').split('\t')
            times[name] = times.get(name, 0.0) + float(sec)


def load_sample(path):
    times = {}
    if os.path.isdir(path):
        for f in sorted(glob.glob(os.path.join(path, '**', 'TEST-*.xml'), recursive=True)):
            read_xml(f, times)
        for f in sorted(glob.glob(os.path.join(path, '**', '*.tsv'), recursive=True)):
            read_tsv(f, times)
    elif path.endswith('.tsv'):
        read_tsv(path, times)
    else:
        sys.exit(f'표본은 폴더나 .tsv 파일이어야 한다: {path}')
    return times


def shard_salt():
    m = re.search(r'val shardSalt = "([^"]*)"', open(GRADLE, encoding='utf-8').read())
    if not m:
        sys.exit(f'{GRADLE} 에서 shardSalt 를 찾지 못했다(배정 규칙이 바뀌었으면 이 스크립트도 고친다).')
    return m.group(1)


def read_times_file(path):
    rows = []
    if os.path.isfile(path):
        with open(path, encoding='utf-8') as f:
            for line in f:
                if line.strip() and not line.startswith('#'):
                    name, sec = line.rstrip('\n').split('\t')
                    rows.append((name, float(sec)))
    return rows


def assign(file_rows, shards, salt):
    """build.gradle.kts 와 같은 배정. 돌려주는 함수: 클래스 경로 → 조각 번호(0부터)."""
    load = [0.0] * shards
    known = {}
    for name, sec in sorted(file_rows, key=lambda r: (-r[1], r[0])):
        best = 0
        for i in range(1, shards):
            if load[i] < load[best]:
                best = i
        load[best] += sec
        known[name] = best
    return lambda k: known[k] if k in known else java_hash(k + salt) % shards   # 파이썬 % 는 Math.floorMod 와 같다


def report(label, shard_of, samples, shards):
    worst = 0.0
    print(label)
    for p, t in samples:
        s = [0.0] * shards
        for k, sec in t.items():
            s[shard_of(k)] += sec
        ideal = sum(s) / shards
        ratio = max(s) / ideal
        worst = max(worst, ratio)
        print(f'    {p}: 조각별 초 {[round(x) for x in s]}  가장 느린 조각 {max(s):.0f}초 / 이상값 {ideal:.0f}초 = {ratio:.3f}배')
    return worst


def main():
    ap = argparse.ArgumentParser(description='서버 시험 조각 균형 보기·시간 파일 갱신')
    ap.add_argument('samples', nargs='*', help='표본 폴더 또는 .tsv(없으면 로컬 시험 결과 하나)')
    ap.add_argument('--shards', type=int, default=3)
    ap.add_argument('--write', action='store_true', help=f'표본들로 {TIMES} 를 새로 쓴다')
    ap.add_argument('--export', metavar='TSV', help='로컬 시험 결과 XML 을 클래스별 시간 TSV 로 쓰고 끝낸다(CI 산출물용)')
    a = ap.parse_args()

    if a.export:
        times = load_sample(DEFAULT_XML)
        if not times:
            sys.exit(f'{DEFAULT_XML} 에 시험 결과 XML 이 없다.')
        with open(a.export, 'w', encoding='utf-8') as f:
            f.write('# 클래스 경로\t초 (tools/ci/shard-balance.py --export)\n')
            for k in sorted(times):
                f.write(f'{k}\t{times[k]:.3f}\n')
        print(f'{a.export}: 클래스 {len(times)}개, 합 {sum(times.values()):.0f}초')
        return

    samples = []
    for p in a.samples or [DEFAULT_XML]:
        t = load_sample(p)
        if not t:
            sys.exit(f'{p}: 시험 시간 기록이 없다. 먼저 :furnace-server:test 를 돌리거나 CI 산출물 shard-times-N 을 받는다.')
        samples.append((p, t))

    # 지금 시험 소스에 없는 클래스(지운·옮긴 시험)는 뺀다.
    if os.path.isdir(TEST_SRC):
        for i, (p, t) in enumerate(samples):
            gone = [k for k in t if not os.path.exists(os.path.join(TEST_SRC, k + '.java'))]
            if gone:
                print(f'  {p}: 지금 소스에 없는 클래스 {len(gone)}개 제외({sum(t[k] for k in gone):.0f}초)')
                samples[i] = (p, {k: v for k, v in t.items() if k not in gone})

    salt = shard_salt()
    n = a.shards
    file_rows = read_times_file(TIMES)
    print(f'표본 {len(samples)}개, 조각 {n}개, 시간 파일 {TIMES}: 클래스 {len(file_rows)}개')
    before = report('지금 배정:', assign(file_rows, n, salt), samples, n)

    if a.write:
        # 표본마다 합을 평균 합에 맞춘 뒤(빠른 컴퓨터·느린 컴퓨터를 같은 눈금으로) 클래스별 평균. 일부 표본에만 있는 클래스는 있는 표본만으로.
        mean_total = sum(sum(t.values()) for _, t in samples) / len(samples)
        acc = {}
        for _, t in samples:
            scale = mean_total / sum(t.values())
            for k, v in t.items():
                acc.setdefault(k, []).append(v * scale)
        rows = sorted((k, round(sum(v) / len(v), 1)) for k, v in acc.items())
        with open(TIMES, 'w', encoding='utf-8') as f:
            f.write('# 서버 시험 클래스별 시간(초). -PtestShard 배정용(furnace-server/build.gradle.kts, T-162).\n')
            f.write('# 손으로 고치지 말고 tools/ci/shard-balance.py --write <표본...> 로 다시 만든다(docs/ci.md "조각 균형").\n')
            for k, v in rows:
                f.write(f'{k}\t{v}\n')
        print(f'{TIMES} 를 새로 썼다: 클래스 {len(rows)}개, 표본 {len(samples)}개 평균')
        after = report('새 배정:', assign(rows, n, salt), samples, n)
        print(f'→ 가장 나쁜 표본의 가장 느린 조각 {before:.3f}배 → {after:.3f}배(이상값 대비, 같은 표본으로 잰 값이라 실제 이득은 이보다 작을 수 있다)')
        file_rows = rows

    # 시간 파일에 없어 해시로 가는 클래스가 표본에서 차지하는 몫 — 크면 --write 로 갱신할 때다.
    names = {k for k, _ in file_rows}
    for p, t in samples:
        miss = {k: v for k, v in t.items() if k not in names}
        if miss:
            share = 100 * sum(miss.values()) / sum(t.values())
            top = sorted(miss, key=miss.get, reverse=True)[:5]
            print(f'  {p}: 시간 파일에 없는 클래스 {len(miss)}개({share:.1f}% 시간)가 해시로 간다: {", ".join(x.rsplit("/", 1)[-1] for x in top)}{" …" if len(miss) > 5 else ""}')


if __name__ == '__main__':
    main()
