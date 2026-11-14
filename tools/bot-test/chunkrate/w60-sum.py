# w60-run.js 가 쌓은 runs/w60.jsonl 을 판마다 한 줄 JSON 으로 요약한다.
# 사용: python3 -I w60-sum.py [꼬리표 정규식]      (W60_ROOT 환경변수 = w60-run.js 의 작업 폴더, 기본 <임시폴더>/w60)
# 내용: 봇 결과(받은 청크/초·링 0~2 평균·최저·p10·0명 수·이동 검증) + 표본(CPU·TPS·mspt) + 서버 [CHUNKTRACE](생성/초·원형/완성·대기·gqstale).
# 주의
#  - gen.queue·gen.run·gen.pristine·gen.drop·gen.cancel·batch.gqlen·batch.gqstale 은 시제품/T-170 이후 서버만 찍는다. 없으면 -1·0 으로 나온다.
#  - gen.queue(생성 대기 시간)는 "시작한 요청"만 잰다 — 시작 못 하고 큐에 남은(굶는) 요청은 안 들어가므로 생존자 편향이다. 굶김은 gqstale(큐 안 시야 밖 요청 수)·ring02Min·ring02Zero 로 본다.
#  - batch.gqstale 은 통과 조건 "0" 이어야 한다(T-170). gqstale_max·gqstale_last 로 5초 창 최댓값·마지막 값을 적는다.
#  - 시각은 서버 로그와 같은 컴퓨터의 현지 시각으로 맞춘다(서버·요약이 같은 컴퓨터여야 함).
import json, csv, re, sys, os, statistics as st, datetime, collections, tempfile
S = os.environ.get('W60_ROOT') or os.path.join(tempfile.gettempdir(), 'w60')
RUNS = os.path.join(S, 'runs')
only = sys.argv[1] if len(sys.argv) > 1 else None
hdr = None
def secs_of_day(ts): d = datetime.datetime.fromtimestamp(ts); return d.hour * 3600 + d.minute * 60 + d.second
for l in open(os.path.join(RUNS, 'w60.jsonl'), encoding='utf8').read().splitlines():
    if l.startswith('== tag='):
        hdr = dict(kv.split('=', 1) for kv in l[3:].split() if '=' in kv); continue
    if not l.startswith('{') or hdr is None: continue
    if only and not re.match(only, hdr['tag']): continue
    j = json.loads(l); W = hdr['world']; a, b = j['walkStart'], j['walkEnd']
    rows = []
    for fn in sorted(os.listdir(os.path.join(RUNS, W))):
        if fn.startswith('samples-') and fn.endswith('.csv'):
            rows += [r for r in csv.DictReader(open(os.path.join(RUNS, W, fn))) if a <= float(r['ts']) <= b]
    f = lambda k: [float(r[k]) for r in rows if r.get(k)]
    g = collections.Counter()
    for r in rows:
        for kv in (r.get('grp') or '').split('|'):
            if '=' in kv: k, v = kv.rsplit('=', 1); g[k] += float(v)
    n = max(1, len(rows))
    grp = {k[:10]: round(v / n) for k, v in g.items() if v / n >= 8}
    ct = collections.defaultdict(lambda: [0, 0.0, 0.0, 0.0])   # n, sum, max, last
    lo, hi = secs_of_day(a) - 1, secs_of_day(b) + 6          # 추적 한 줄 = 직전 5초 창 → 끝에 여유
    for fn in sorted(os.listdir(os.path.join(RUNS, W))):
        if not (fn.startswith('server-') and fn.endswith('.log')): continue
        for L in open(os.path.join(RUNS, W, fn), errors='ignore', encoding='utf8'):
            if 'CHUNKTRACE' not in L: continue
            m0 = re.match(r'\[(\d\d):(\d\d):(\d\d)', L)
            if not m0: continue
            t = int(m0.group(1)) * 3600 + int(m0.group(2)) * 60 + int(m0.group(3))
            if not (lo <= t <= hi): continue
            for m in re.finditer(r'(\S+) n=(\d+) avg=([0-9.]+)(?:ms)? max=([0-9.]+)', L):
                e = ct[m.group(1)]; e[0] += int(m.group(2)); e[1] += int(m.group(2)) * float(m.group(3)); e[2] = max(e[2], float(m.group(4))); e[3] = float(m.group(3))
    avgms = lambda k: round(ct[k][1] / ct[k][0]) if ct[k][0] else -1
    full = ct['gen.run'][0]; secs = max(1.0, b - a)
    to = f('tps_slow_ow'); mo = f('mspt_region_ow')
    out = dict(tag=hdr['tag'], opts=hdr.get('opts', '').replace('-Dfurnace.proto.', '').replace('-XX:', ''), lay=j['layout'], ymode=j.get('ymode', 'fixed(옛)'),
               walk_ok=j.get('walkOk'), moved=f"{j.get('movedZMin', j['movedMin'])}/{j.get('srvMovedMin')} (기대 {j.get('expectedMoved')})", corr=j['serverCorrections'],
               recv_s=j['chunksPerSecTotal'], need_s=j.get('needPerSecTotal'),
               ring02=j['ring02Avg'], r02min=j.get('ring02Min'), r02p10=j.get('ring02P10', (j.get('ring02Pct') or [None])[0]), r02pct=j.get('ring02Pct'), r02zero=j.get('ring02Zero'),
               ring35=j['ring35Avg'], cover=j['coverAvg'], holes=j['holeFrac'], ground=f"hit {j.get('groundHit')}/miss {j.get('groundMiss')}",
               gen_s=round(full / secs, 1), prist_per_full=round(ct['gen.pristine'][0] / max(1, full), 2), drop_per_full=round(ct['gen.drop'][0] / max(1, full), 2), cancel_s=round(ct['gen.cancel'][0] / secs, 1),
               genq_ms=avgms('gen.queue'), genrun_ms=avgms('gen.run'), hopgen_ms=avgms('hop.genpool'), srcgen_ms=avgms('src.gen'),
               gqlen_avg=avgms('batch.gqlen'), gqstale_avg=round(ct['batch.gqstale'][1] / ct['batch.gqstale'][0], 1) if ct['batch.gqstale'][0] else -1, gqstale_max=ct['batch.gqstale'][2], gqstale_last=ct['batch.gqstale'][3],
               cpu=round(st.mean(f('cpu_pct'))) if f('cpu_pct') else None, tps_avg=round(st.mean(to), 2) if to else None, tps_min=round(min(to), 2) if to else None,
               mspt=round(st.mean(mo), 1) if mo else None, regions=round(st.mean(f('regions_ow'))) if f('regions_ow') else None,
               chunks=int(max(f('chunks'))) if f('chunks') else None, heap=int(max(f('heap_mb'))) if f('heap_mb') else None, grp=grp)
    if j.get('walkOk') is False: out['경고'] = '이동 검증 실패 — 수치 믿지 말 것'
    print(json.dumps(out, ensure_ascii=False))
