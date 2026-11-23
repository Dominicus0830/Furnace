#!/usr/bin/env python3
"""엔티티 데이터 칸 상수(DATA_*) 명세 줄을 만든다(엔티티 데이터 설계 D2).

Paper 서버 서명표(tools/compat/sig-tables.sh 가 캐시에 만든다)에서 public/protected 인 EntityDataAccessor 정적 필드만 골라,
값은 실행 때 #565 칸 번호표(entity_data/ids-<판>.txt, 지문)에서 찾게 하는 줄을 쓴다 — 명세에는 번호·지문을 적지 않는다.
private·package 필드는 적지 않는다(필요하면 따로 결정). 다른 명세 파일에 이미 있는 클래스는 그 클래스 블록 안 표지 사이에 넣고,
나머지는 entity-data.spec 에 쓴다. 표지 블록과 entity-data.spec 은 이 도구가 통째로 다시 쓴다(손으로 고치지 않는다).

쓰기: python3 tools/compat/entity-data-spec.py [판=1.21.1] [--check]
"""
import gzip, os, re, sys

MC = next((a for a in sys.argv[1:] if not a.startswith('-')), '1.21.1')
CHECK = '--check' in sys.argv
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = os.path.join(ROOT, 'furnace-compat-paper/src/shimspec')
CACHE = os.environ.get('FURNACE_VANILLA_CACHE') or os.path.expanduser('~/.cache/furnace-vanilla-extract')
TABLE = os.path.join(CACHE, MC, 'sig-table-paper-server.tsv.gz')
BEGIN = '  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)'
END = '  # <<< 엔티티 데이터 칸 상수'
ACC = 'Lnet/minecraft/network/syncher/EntityDataAccessor;'


def line(cls, name):
    return f'  {name} = org.domi.furnace.compat.nms.NmsEntityData.accessor({cls}.class, "{name}")'


def main():
    fields = {}
    with gzip.open(TABLE, 'rt', encoding='utf-8') as f:
        for l in f:
            p = l.rstrip('\n').split('\t')
            if p[0] == 'F' and p[4] == ACC and int(p[1]) & 0x0008 and int(p[1]) & 0x0005:
                fields.setdefault(p[2].replace('/', '.'), []).append(p[3])
    files = {n: open(os.path.join(SPEC, n), encoding='utf-8').read() for n in sorted(os.listdir(SPEC)) if n.endswith('.spec') and n != 'entity-data.spec'}
    out, changed, rest = {}, [], []
    for n, text in files.items():
        lines = [l for l in text.split('\n')]
        # 지난 블록을 지운다
        cleaned, skip = [], False
        for l in lines:
            if l == BEGIN: skip = True; continue
            if l == END: skip = False; continue
            if not skip: cleaned.append(l)
        res = []
        for l in cleaned:
            res.append(l)
            m = re.match(r'class (\S+)', l)
            if m and m.group(1).replace('$', '.') in {c.replace('$', '.') for c in fields}:
                cls = next(c for c in fields if c.replace('$', '.') == m.group(1).replace('$', '.'))
                res.append(BEGIN)
                res += [line(cls.replace('$', '.'), f) for f in sorted(fields[cls])]
                res.append(END)
                fields[cls] = None
        out[n] = '\n'.join(res)
    gen = ['# 엔티티 데이터 칸 상수(엔티티 데이터 설계 D2). tools/compat/entity-data-spec.py 가 Paper 서버 서명표에서 만든다 — 손으로 고치지 않는다.',
           '# public/protected 인 EntityDataAccessor 정적 필드만. 값(칸 번호·값 종류)은 실행 때 NmsEntityData.accessor 가 칸 번호표에서 찾는다.']
    for cls in sorted(c for c, v in fields.items() if v):
        gen.append(f'class {cls}')
        gen += [line(cls.replace('$', '.'), f) for f in sorted(fields[cls])]
    out['entity-data.spec'] = '\n'.join(gen) + '\n'
    for n, text in out.items():
        path = os.path.join(SPEC, n)
        old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
        if old != text:
            changed.append(n)
            if not CHECK:
                with open(path, 'w', encoding='utf-8', newline='\n') as f: f.write(text)
    if CHECK and changed:
        print('entity-data-spec: out of date: ' + ', '.join(changed)); sys.exit(1)
    print(('entity-data-spec: up to date' if not changed else 'entity-data-spec: wrote ' + ', '.join(changed)))


main()
