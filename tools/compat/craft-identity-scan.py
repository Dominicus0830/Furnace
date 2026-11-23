#!/usr/bin/env python3
"""T-264 CraftBukkit 계층 대조 스캔(설계 근거 숫자를 다시 내는 도구).

입력: 개발자 캐시의 서명표 두 개(tools/compat/sig-tables.sh 가 만든 것 — 커밋·배포 금지)와 컴파일된 호환 계층 클래스 폴더.
출력: 숫자와 CraftBukkit·Bukkit API 이름만(Mojang 이름은 세기만 하고 찍지 않는다). 결과를 커밋하지 않는다.

  python3 -I tools/compat/craft-identity-scan.py [--list] [클래스 폴더=furnace-compat-paper/build/classes/java/main] [판=1.21.1]

분류(설계 문서 docs/feature-parity/compat-craft-hierarchy-t264-design-2026-11-23.md 2장):
  - impl: Paper Craft 클래스가 (상위 사슬로) Bukkit/Paper API 형식을 구현 — 플러그인 손에 쥐어지는 객체의 실제 클래스.
  - static: 정적 멤버만(변환 도구), internal: 그 밖(서버 내부).
  impl 은 다시 Furnace 쪽 상태로: 같은 이름·같은 상위 / 같은 이름·다른 상위 / 껍데기인데 Furnace 가 그 API 를 다른 이름 클래스로 구현 /
  껍데기이고 Furnace 구현 없음(그 API 형식이 Furnace 에 있나 없나) / 범용 표지 인터페이스뿐.
"""
import collections
import gzip
import os
import struct
import sys

CACHE = os.path.expanduser('~/.cache/furnace-vanilla-extract')
CB = 'org/bukkit/craftbukkit/'
# 거의 모든 객체가 구현하는 표지 인터페이스 — "어느 Furnace 클래스가 같은 구실인가"를 가를 때 보지 않는다.
GENERIC = {
    'org/bukkit/Keyed', 'org/bukkit/configuration/serialization/ConfigurationSerializable', 'net/kyori/adventure/key/Keyed',
    'net/kyori/adventure/translation/Translatable', 'org/bukkit/Translatable', 'io/papermc/paper/world/flag/FeatureDependant',
    'org/bukkit/metadata/Metadatable', 'org/bukkit/persistence/PersistentDataHolder', 'net/kyori/adventure/text/event/HoverEventSource',
    'net/kyori/adventure/audience/Audience', 'net/kyori/adventure/audience/ForwardingAudience', 'net/kyori/adventure/pointer/Pointered',
    'org/bukkit/permissions/ServerOperator', 'org/bukkit/permissions/Permissible', 'org/bukkit/inventory/InventoryHolder',
    'net/kyori/adventure/sound/Sound$Emitter', 'io/papermc/paper/datacomponent/DataComponentHolder',
}


def load_table(path):
    classes, methods = {}, collections.defaultdict(list)
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        for line in f:
            if line.startswith('#'):
                continue
            t = line.rstrip('\n').split('\t')
            if t[0] == 'C':
                classes[t[2]] = dict(acc=int(t[1]), sup=None if t[3] == '-' else t[3], itf=[] if t[4] == '-' else t[4].split(','))
            elif t[0] == 'M':
                methods[t[2]].append((int(t[1]), t[3], t[4]))
    return classes, methods


def parse_class(b):
    """클래스 파일 머리(접근·이름·상위·인터페이스)만 읽는다."""
    i, cp, n, k = 10, [None], struct.unpack('>H', b[8:10])[0], 1
    while k < n:
        t = b[i]
        if t == 1:
            ln = struct.unpack('>H', b[i + 1:i + 3])[0]
            cp.append(('u', b[i + 3:i + 3 + ln].decode('utf-8', 'replace')))
            i += 3 + ln
        elif t in (3, 4):
            cp.append(None); i += 5
        elif t in (5, 6):
            cp += [None, None]; i += 9; k += 1
        elif t == 7:
            cp.append(('c', struct.unpack('>H', b[i + 1:i + 3])[0])); i += 3
        elif t in (8, 16, 19, 20):
            cp.append(None); i += 3
        elif t in (9, 10, 11, 12, 17, 18):
            cp.append(None); i += 5
        elif t == 15:
            cp.append(None); i += 4
        else:
            raise ValueError('constant pool tag %d' % t)
        k += 1

    def cname(idx):
        return cp[cp[idx][1]][1] if idx else None

    acc, this, sup, ic = struct.unpack('>HHHH', b[i:i + 8])
    i += 8
    itf = [cname(struct.unpack('>H', b[i + 2 * j:i + 2 * j + 2])[0]) for j in range(ic)]
    return dict(acc=acc, name=cname(this), sup=cname(sup), itf=itf)


def load_classes(root):
    out = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith('.class'):
                with open(os.path.join(d, f), 'rb') as fh:
                    c = parse_class(fh.read())
                out[c['name']] = c
    return out


def closure(n, table):
    seen, st = set(), [n]
    while st:
        x = st.pop()
        if x in seen or x not in table:
            continue
        seen.add(x)
        c = table[x]
        if c['sup']:
            st.append(c['sup'])
        st.extend(c['itf'])
    return seen


def main(argv):
    listing = '--list' in argv
    args = [a for a in argv if a != '--list']
    root = args[0] if args else 'furnace-compat-paper/build/classes/java/main'
    mc = args[1] if len(args) > 1 else '1.21.1'
    pc, pm = load_table(os.path.join(CACHE, mc, 'sig-table-paper-server.tsv.gz'))
    ac, am = load_table(os.path.join(CACHE, mc, 'sig-table-paper-api.tsv.gz'))
    fur = load_classes(root)
    both = {**ac, **pc}

    rows = {}
    for c in sorted(x for x in pc if x.startswith(CB)):
        apis = sorted(x for x in closure(c, both) if x in ac)
        api_m = {(n, d) for x in apis for acc, n, d in am.get(x, []) if not acc & 0x8}
        own = pm.get(c, [])
        impl = [m for m in own if (m[1], m[2]) in api_m and not m[0] & 0x400]
        non_ctor = [m for m in own if m[1] != '<init>']
        if apis:
            kind = 'impl'
        elif non_ctor and all(m[0] & 0x8 for m in non_ctor):
            kind = 'static'
        else:
            kind = 'internal'
        rows[c] = dict(kind=kind, apis=apis, direct=[x for x in pc[c]['itf'] if x in ac], nimpl=len(impl), sup=pc[c]['sup'], acc=pc[c]['acc'])

    # Furnace 의 구체 클래스가 구현하는 API 형식 → 그 클래스들
    by_api = collections.defaultdict(set)
    for n, c in fur.items():
        if c['acc'] & 0x200:
            continue
        for x in closure(n, fur):
            if x in ac and x != n:
                by_api[x].add(n)

    state = {}
    cands = {}   # furnace-other-name: 같은 API 를 구현하는 Furnace 클래스(--list 에 찍는다)
    for c, r in rows.items():
        if r['kind'] != 'impl':
            continue
        if c in fur:
            state[c] = 'same-super' if fur[c]['sup'] == r['sup'] else 'other-super'
            continue
        key = [i for i in r['direct'] if i not in GENERIC]
        if not key:
            # 직접 단 API 가 없으면 상위 사슬의 API 중 가장 구체적인 것(다른 API 의 상위가 아닌 것) 전부 — 순서와 무관하게 같은 답
            cand = {i for i in r['apis'] if i not in GENERIC}
            key = sorted(i for i in cand if not any(i != j and i in closure(j, both) for j in cand))
        if not key:
            state[c] = 'generic-only'
            continue
        if any(by_api.get(i) for i in key):
            state[c] = 'furnace-other-name'
            cands[c] = sorted({n for i in key for n in by_api.get(i, ())})
        elif any(i in fur for i in key):
            state[c] = 'no-impl:api-type-in-furnace'
        else:
            state[c] = 'no-impl:api-type-missing'

    kinds = collections.Counter(r['kind'] for r in rows.values())
    print('Craft classes', len(rows), dict(kinds))
    st = collections.Counter(state.values())
    print('impl by Furnace state', dict(st))
    print('API method impls (own, non-abstract) by state',
          dict(collections.Counter({s: 0 for s in st}) + collections.Counter({s: sum(rows[c]['nimpl'] for c in state if state[c] == s) for s in st})),
          'total', sum(rows[c]['nimpl'] for c in state))
    anon = [c for c in state if '$' in c and c.rsplit('$', 1)[1].isdigit()]
    print('anonymous impl classes', len(anon))
    top = [c for c in rows if '$' not in c]
    print('top-level', len(top), 'non-public', sum(1 for c in top if not rows[c]['acc'] & 1),
          'final', sum(1 for c in top if rows[c]['acc'] & 0x10), 'abstract', sum(1 for c in top if rows[c]['acc'] & 0x400 and not rows[c]['acc'] & 0x200))
    gh = [(c, d[3:-1]) for c in rows for acc, n, d in pm.get(c, []) if n == 'getHandle' and d.startswith('()L')]
    print('covariant getHandle()', len(gh), 'return type present in compat jar', sum(1 for _, t in gh if t in fur))
    blk = collections.Counter(state[c] for c in state if c.startswith(CB + 'block/impl/') or c.startswith(CB + 'block/data/'))
    print('block data family', dict(blk))
    if listing:
        for c in sorted(state):
            print(state[c], c[len(CB):], 'super=' + (rows[c]['sup'] or '-').replace(CB, ''),
                  'furnace-super=' + ((fur[c]['sup'] or '-').replace(CB, '') if c in fur else '-'),
                  *(['furnace=' + ','.join(x.rsplit('/', 1)[-1] for x in cands[c][:6])] if c in cands else []))


if __name__ == '__main__':
    main(sys.argv[1:])
