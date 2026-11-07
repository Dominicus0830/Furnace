#!/usr/bin/env python3
"""블록별 SoundType(BlockBehaviour.Properties.sound)을 바닐라 1.21.1 서버 클래스의 바이트코드에서 읽어 낸다.

바닐라 서버를 실행할 수 없는 환경(Mojang 라이브러리 호스트 접근 불가)에서도 쓰려고, Bootstrap 실행 대신 `javap -c -p` 출력만 읽는다.
1.21.1 난독화 이름 전용(SoundType=dnm, SoundEvents=avp, Blocks=dga, Properties=dtb$d, BlockSetType=dtr, WoodType=dup, 블록 키=akh).
사용: extract_block_sound_types.py <서버 클래스 디렉터리> <출력 txt>

방법
  1. dnm.<clinit>: 상수마다 (크기, 높이, break/step/place/hit/fall 소리 필드) → avp.<clinit> 으로 소리 이름을 찾는다.
  2. dga.<clinit>: 블록마다 `ldc 이름 ... putstatic` 한 덩이를 훑는다. 상태는 Properties 의 sound(dnm) 호출,
     ofFullCopy/ofLegacyCopy(다른 블록) 호출, 도우미 정적 메서드 호출(안으로 들어가 같은 규칙), 그리고
     BlockSetType/WoodType 을 받는 블록 생성자가 `properties.sound(type.soundType())` 를 부르는 경우(문·다락문·버튼·압력판·울타리 문·표지판)를 본다.
  3. 아무 호출도 없으면 Properties 기본 STONE.
출력 형식은 txt 머리 주석 참고.
"""
import json, os, re, struct, subprocess, sys

CP = sys.argv[1]
OUT = sys.argv[2]
SOUND_TYPE, SOUND_EVENTS, BLOCKS, PROPS = 'dnm', 'avp', 'dga', 'dtb$d'
# BlockSetType/WoodType 의 soundType()/hangingSignSoundType() 를 properties.sound(...) 로 넘기는 블록 생성자를 가진 클래스
DIRECT_ACCESSOR = {'dfm': 'dtr.g', 'dgi': 'dtr.g', 'die': 'dtr.g', 'dom': 'dtr.g',
                   'dgx': 'dup.e', 'doy': 'dup.e', 'doz': 'dup.d', 'dnu': 'dup.d', 'dix': 'dup.d'}


def javap(cls):
    r = subprocess.run(['javap', '-p', '-c', '-cp', CP, cls], capture_output=True, text=True,
                       env={'PATH': os.environ.get('PATH', '/usr/bin:/bin')})
    return r.stdout.split('\n')


def string_fields(cls):
    """<clinit> 의 `ldc 문자열 ... putstatic 필드` → {필드: 문자열}."""
    res, last = {}, None
    for l in javap(cls):
        m = re.search(r'// String (\S+)$', l)
        if m:
            last = m.group(1)
        m = re.search(r'putstatic\s+#\d+\s+// Field (\w+):L', l)
        if m and last:
            res[m.group(1)] = last
            last = None
    return res


def read_super_chain():
    """클래스 파일 상수 풀만 읽어 {클래스: 부모}."""
    supers = {}
    for f in os.listdir(CP):
        if not f.endswith('.class'):
            continue
        b = open(os.path.join(CP, f), 'rb').read()
        pos = 8
        n = struct.unpack('>H', b[pos:pos + 2])[0]
        pos += 2
        cp = [None] * n
        i = 1
        while i < n:
            t = b[pos]
            pos += 1
            if t == 1:
                ln = struct.unpack('>H', b[pos:pos + 2])[0]
                cp[i] = ('u', b[pos + 2:pos + 2 + ln].decode('utf8', 'replace'))
                pos += 2 + ln
            elif t in (3, 4):
                pos += 4
            elif t in (5, 6):
                pos += 8
                i += 1
            elif t in (7, 8, 16, 19, 20):
                cp[i] = (t, struct.unpack('>H', b[pos:pos + 2])[0])
                pos += 2
            elif t in (9, 10, 11, 12, 17, 18):
                pos += 4
            elif t == 15:
                pos += 3
            else:
                raise ValueError(t)
            i += 1
        _, this, sup = struct.unpack('>HHH', b[pos:pos + 6])
        name = lambda idx: cp[cp[idx][1]][1] if idx else None
        supers[name(this)] = name(sup)
    return supers


events = string_fields(SOUND_EVENTS)

# 1. SoundType 상수
sound_types = {}
cur = []
for l in javap(SOUND_TYPE):
    if re.search(r'\bnew\s+#\d+\s+// class ' + SOUND_TYPE + '$', l):
        cur = []
    if 'getstatic' not in l:
        if 'fconst_1' in l:
            cur.append(1.0)
        elif 'fconst_0' in l:
            cur.append(0.0)
        else:
            m = re.search(r'ldc\w*\s+#\d+\s+// float ([\d.]+)f', l)
            if m:
                cur.append(float(m.group(1)))
    m = re.search(r'getstatic\s+#\d+\s+// Field ' + SOUND_EVENTS + r'\.(\w+):', l)
    if m:
        cur.append(m.group(1))
    m = re.search(r'putstatic\s+#\d+\s+// Field (\w+):L' + SOUND_TYPE + ';', l)
    if m:
        vol, pitch, evs = cur[0], cur[1], [events[x] for x in cur[2:7]]
        sound_types[m.group(1)] = (vol, pitch, evs)
        cur = []

# 2. BlockSetType / WoodType 상수의 소리 종류 (기본 WOOD / HANGING_SIGN)
def type_constants(cls, default):
    res, snds, name = {}, [], None
    lines = javap(cls)
    start = next(k for k, l in enumerate(lines) if l.startswith('  static {}'))
    for l in lines[start:]:
        m = re.search(r'// String (\S+)$', l)
        if m and name is None:
            name = m.group(1)
        m = re.search(r'getstatic\s+#\d+\s+// Field ' + SOUND_TYPE + r'\.(\w+):', l)
        if m:
            snds.append(m.group(1))
        m = re.search(r'putstatic\s+#\d+\s+// Field (\w+):L' + cls + ';', l)
        if m:
            res[cls + '.' + m.group(1)] = (snds[0] if snds else default[0], snds[1] if len(snds) > 1 else default[1])
            snds, name = [], None
    return res


# WOOD, HANGING_SIGN 필드를 SoundType 표에서 찾는다: dtr/dup 기본 생성자가 부르는 상수
def default_sound(cls, ctor_args, idx):
    lines = javap(cls)
    i = next(k for k, l in enumerate(lines) if ('public ' + cls + '(' + ctor_args + ');') in l)
    fields = []
    for l in lines[i:i + 40]:
        m = re.search(r'getstatic\s+#\d+\s+// Field ' + SOUND_TYPE + r'\.(\w+):', l)
        if m:
            fields.append(m.group(1))
        if 'invokespecial' in l:
            break
    return fields[idx]


wood = default_sound('dtr', 'java.lang.String', 0)
hanging = default_sound('dup', 'java.lang.String, dtr', 1)
type_consts = type_constants('dtr', (wood, wood))
for k, v in type_constants('dup', (wood, hanging)).items():
    type_consts[k] = v

supers = read_super_chain()


def accessor(c):
    while c:
        if c in DIRECT_ACCESSOR:
            return DIRECT_ACCESSOR[c]
        c = supers.get(c)
    return None


def type_sound(const, acc):
    snd, hang = type_consts[const]
    return hang if acc == 'dup.e' else snd


# 3. Blocks 정적 초기화
block_lines = javap(BLOCKS)
akh = string_fields('akh')
methods = {}
cur_m = None
for l in block_lines:
    m = re.match(r'  (?:[\w$.<>\[\]]+ )*([\w<>"]+)\((.*?)\)(?: throws .*)?;$', l)
    if m and not l.startswith('   '):
        cur_m = (m.group(1), m.group(2))
        methods[cur_m] = []
        continue
    if l.startswith('  static {}'):
        cur_m = ('<clinit>', '')
        methods[cur_m] = []
        continue
    m = re.match(r'\s+\d+: (\w+)\s*(.*)$', l)
    if m and cur_m is not None:
        methods[cur_m].append((m.group(1), m.group(2)))


def field(s):
    m = re.search(r'// Field (\S+?):(\S+)$', s)
    return (m.group(1), m.group(2)) if m else None


def method(s):
    m = re.search(r'// (?:Interface)?Method ([^\s:(]+?)\.(\S+?):(\S+)$', s)
    if m:
        return m.group(1), m.group(2), m.group(3)
    m = re.search(r'// (?:Interface)?Method ("?[\w<>$]+"?):(\S+)$', s)
    return (BLOCKS, m.group(1), m.group(2)) if m else None


def desc_args(d):
    inner = d[1:d.index(')')]
    res, i = [], 0
    while i < len(inner):
        c, arr = inner[i], ''
        while c == '[':
            arr += '[]'
            i += 1
            c = inner[i]
        if c == 'L':
            j = inner.index(';', i)
            res.append(inner[i + 1:j].replace('/', '.') + arr)
            i = j + 1
        else:
            res.append({'I': 'int', 'F': 'float', 'Z': 'boolean', 'D': 'double', 'J': 'long'}[c] + arr)
            i += 1
    return ', '.join(res)


block_sound = {}   # 블록 필드 → SoundType 필드
DEFAULT = 'f'      # Properties 기본 SoundType.STONE (dtb$d.<init> 의 getstatic dnm.f)


def simulate(body, p_sound=None, p_block=None, p_type=None):
    cur_s, last_s, last_b, last_t = DEFAULT, p_sound, p_block, p_type
    segname, out = None, []
    for op, rest in body:
        if op in ('ldc', 'ldc_w') and '// String ' in rest and segname is None:
            segname = rest.split('// String ')[1]
        f = field(rest) if op in ('getstatic', 'putstatic') else None
        if op == 'getstatic' and f:
            if f[0].startswith('akh.') and segname is None:
                segname = akh[f[0][4:]]
            if f[0].startswith(SOUND_TYPE + '.'):
                last_s = f[0].split('.')[1]
            if f[0] in type_consts:
                last_t = f[0]
            if '.' not in f[0]:
                last_b = f[0]
        mm = method(rest) if op.startswith('invoke') else None
        if mm:
            cls, name, desc = mm
            if name == '"<init>"' and accessor(cls) and last_t:
                cur_s = type_sound(last_t, accessor(cls))
            elif cls == PROPS and name == 'a' and desc == '(L%s;)L%s;' % (SOUND_TYPE, PROPS):
                cur_s = last_s
            elif cls == PROPS and name in ('a', 'b') and desc == '(Ldtb;)L%s;' % PROPS:
                cur_s = block_sound.get(last_b, '??' + str(last_b))
            elif cls == BLOCKS and desc in ('(Ljava/lang/String;Ldfy;)Ldfy;', '(Lakq;Ldfy;)Ldfy;'):
                pass   # 등록 함수
            elif cls == BLOCKS and (desc.endswith(')Ldfy;') or desc.endswith(')L%s;' % PROPS)):
                cands = [k for k in methods if k[0] == name and k[1] == desc_args(desc)]
                cur_s = simulate(methods[cands[0]], last_s, last_b, last_t)[0] if cands else '?helper ' + name + desc
        if op == 'putstatic' and f and segname is not None and f[1].startswith('L'):
            out.append((segname, f[0], cur_s))
            block_sound[f[0]] = cur_s
            segname, cur_s = None, DEFAULT
    return cur_s, out


_, registered = simulate(methods[('<clinit>', '')])
bad = [(n, s) for n, _, s in registered if s.startswith('?')]
if bad:
    sys.exit('풀지 못한 블록: %s' % bad[:10])

# 3.5 BlockState.getSoundType 을 덮는 블록(바닐라 1.21.1 에서 dtb.g_(dtc) 를 덮는 클래스는 dhy=DecoratedPotBlock 하나뿐):
#     금 간(cracked) 상태이면 DECORATED_POT_CRACKED(dnm.bc), 아니면 DECORATED_POT(dnm.bb). Blocks 쪽 decorated_pot 에는 sound() 호출이 없다.
STATE_OVERRIDES = [('decorated_pot', 'cracked', 'true', 'bc')]
registered = [(n, x, 'bb' if n == 'decorated_pot' else s) for n, x, s in registered]
for _, _, _, k in STATE_OVERRIDES:
    assert k in sound_types, k

# 4. 출력: 소리 종류 표 + 블록 → 종류
used = sorted({s for _, _, s in registered} | {k for _, _, _, k in STATE_OVERRIDES}, key=lambda k: (len(k), k))
index = {k: i for i, k in enumerate(used)}
with open(OUT, 'w', encoding='utf-8') as o:
    o.write('# 자동 생성(tools/vanilla-data/extract_block_sound_types.py, 바닐라 1.21.1 서버 바이트코드): 블록별 SoundType.\n')
    o.write('# [types] 번호 크기 높이 break step place hit fall (소리 이름은 minecraft: 생략)\n')
    o.write('# [blocks] 블록 이름 종류번호 (BlockBehaviour.Properties.sound; 문·다락문·버튼·압력판·울타리 문·표지판은 BlockSetType/WoodType 소리)\n')
    o.write('[types]\n')
    for k in used:
        vol, pitch, evs = sound_types[k]
        o.write('%d %s %s %s\n' % (index[k], vol, pitch, ' '.join(evs)))
    o.write('[blocks]\n')
    for name, _, s in registered:
        o.write('%s %d\n' % (name, index[s]))
    o.write('[state-overrides]\n')
    for name, prop, val, k in STATE_OVERRIDES:
        o.write('%s %s=%s %d\n' % (name, prop, val, index[k]))
print('types %d blocks %d' % (len(used), len(registered)))
