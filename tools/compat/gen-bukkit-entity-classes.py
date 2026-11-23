#!/usr/bin/env python3
"""Bukkit 엔티티 인터페이스·CraftBukkit 엔티티 클래스 생성기(T-141 D).

입력: tools/compat/bukkit-entity-hierarchy-1.21.1.txt (Paper 1.21.1 등록표·인터페이스 계층, 이름만).
출력(furnace-compat-paper/src/main/java):
  - org/bukkit/entity/**: 손으로 쓴 것이 없는 인터페이스를 표시 인터페이스(메서드 없음)로. 상위 형식은 org.bukkit.entity 안의 것만.
  - org/bukkit/craftbukkit/entity/Craft*.java: 손으로 쓴 것이 없는 종류별 클래스와 그 상위 사슬의 중간 계층(T-264). 상위·직접 인터페이스·abstract/final 은
    Paper 와 같다(잠금 furnace-compat-paper/src/shimspec/craft-hierarchy-1.21.1.lock). Furnace 에 아직 없는 인터페이스는 빼고 만든다(기준선에 남는다).
  - org/domi/furnace/compat/paper/CompatEntityKinds.java: 엔티티 종류 이름 → 감싸개 만들기.
plugin 이 instanceof Villager·Minecart 처럼 형을 가르는 것이 목적이다. 메서드는 필요할 때 손으로 인터페이스에 옮겨 쓴다
(그때는 머리말 줄을 지워 생성기가 덮어쓰지 않게 한다).

쓰기: python3 tools/compat/gen-bukkit-entity-classes.py  (저장소 뿌리에서)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'furnace-compat-paper/src/main/java')
DATA = os.path.join(ROOT, 'tools/compat/bukkit-entity-hierarchy-1.21.1.txt')
HEADER = '// 생성됨: tools/compat/gen-bukkit-entity-classes.py — 손으로 고치려면 이 줄을 지운다.'

# 손으로 쓴 인터페이스의 메서드 → 그 인터페이스를 조상으로 둔 생성 Craft 클래스에 넣을 구현
MIXINS = {
    'org.bukkit.entity.Tameable': [
        '    @Override',
        '    public boolean isTamed() { return org.domi.furnace.compat.paper.EntityReads.tamed(furnaceHandle()); }',
    ],
}
# 손으로 만든 종류(생성하지 않고 지금 길을 그대로 쓴다)
SKIP_TYPES = {'PLAYER', 'ITEM', 'ARMOR_STAND', 'PIG', 'UNKNOWN'}


LOCK = os.path.join(ROOT, 'furnace-compat-paper/src/shimspec/craft-hierarchy-1.21.1.lock')


def read_lock():
    """T-264 잠금: 내부 이름 → {acc, sup, itf}."""
    out = {}
    for line in open(LOCK, encoding='utf-8'):
        if not line.startswith('K '):
            continue
        _, name, acc, sup, itf, _role = line.split()
        out[name] = {'acc': int(acc, 16), 'sup': sup, 'itf': [] if itf == '-' else itf.split(',')}
    return out


def hand_constructors():
    """손으로 쓴 Craft 엔티티 클래스(머리말 없음) → Furnace 생성자가 받는 핸들 형."""
    out = {}
    d = os.path.join(SRC, 'org/bukkit/craftbukkit/entity')
    for f in os.listdir(d):
        if not f.endswith('.java'):
            continue
        path = os.path.join(d, f)
        if generated(path):
            continue
        simple = f[:-5]
        m = re.search(r'(?:public|protected)\s+' + simple + r'\(\s*([\w.]+)\s+handle\s*\)', open(path, encoding='utf-8').read())
        if m:
            out['org/bukkit/craftbukkit/entity/' + simple] = m.group(1)
    return out


def path_of(fq):
    return os.path.join(SRC, fq.replace('.', '/') + '.java')


def generated(path):
    if not os.path.exists(path):
        return True
    with open(path, encoding='utf-8') as f:
        return f.readline().startswith(HEADER)


def main():
    types, sups = [], {}
    for line in open(DATA, encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        p = line.split()
        if p[0] == 'T':
            types.append((p[1], p[2], p[3]))
        else:
            sups[p[1]] = p[2:]

    def ancestors(fq):
        out, todo = [], [fq]
        while todo:
            x = todo.pop()
            for s in sups.get(x, []):
                if s not in out:
                    out.append(s)
                    todo.append(s)
        return out

    written = 0
    # 1) 표시 인터페이스
    for fq, ss in sorted(sups.items()):
        path = path_of(fq)
        if not generated(path):
            continue
        pkg, simple = fq.rsplit('.', 1)
        ext = ', '.join(s if s.rsplit('.', 1)[0] != pkg else s.rsplit('.', 1)[1] for s in ss)
        if fq == 'org.bukkit.entity.Entity':
            continue
        body = (f'{HEADER}\npackage {pkg};\n\n'
                f'/** Paper 1.21.1 {simple} 의 형(종류 가르기용). 메서드는 아직 없다. */\n'
                f'public interface {simple}{" extends " + ext if ext else ""} {{\n}}\n')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(body)
        written += 1

    # 2) Craft 클래스와 만들기 표 — T-264: 상위 사슬은 Paper 와 같게(잠금 src/shimspec/craft-hierarchy-<판>.lock).
    #    손 클래스가 없는 중간 계층(CraftMonster·CraftAbstractHorse·PaperSchoolableFish 등)도 만든다.
    lock = read_lock()
    hand_ctor = hand_constructors()

    def ctor_type(cls):
        """cls(내부 이름)의 Furnace 생성자가 받는 핸들 형 — 손 클래스는 소스에서, 생성 클래스는 상위에서."""
        if cls in hand_ctor:
            return hand_ctor[cls]
        e = lock.get(cls)
        if e is None:
            raise SystemExit('잠금에 없음: ' + cls)
        return ctor_type(e['sup'])

    def is_interface(cls):
        return cls in lock and lock[cls]['acc'] & 0x200

    def available(fq_internal):
        """Furnace 호환 소스에 있거나 이번에 만드는 형인가."""
        return os.path.exists(os.path.join(SRC, fq_internal + '.java')) or fq_internal in plan

    abstract_re = re.compile(r'^\s+(?!default\b|static\b|private\b|return\b)[\w<>\[\],.? ]+\s+\w+\s*\([^;{]*\)\s*(?:throws [\w., ]+)?;', re.M)

    def has_abstract(fq_internal, seen=None):
        """Furnace 소스의 인터페이스(와 상위 인터페이스)에 몸통 없는 메서드가 있나 — 있으면 생성 클래스가 붙일 수 없다(몸통이 생길 때까지 기준선 줄)."""
        seen = seen if seen is not None else set()
        if fq_internal in seen or fq_internal.startswith('org/bukkit/entity/'):
            return False   # 엔티티 API 는 손 사슬(CraftEntity·CraftLivingEntity…)과 기본 몸통이 채운다(컴파일이 확인)
        seen.add(fq_internal)
        path = os.path.join(SRC, fq_internal + '.java')
        if fq_internal in plan and not os.path.exists(path):
            return any(has_abstract(i, seen) for i in plan[fq_internal]['itf'] if available(i))
        if not os.path.exists(path):
            return False
        src = open(path, encoding='utf-8').read()
        if abstract_re.search(src):
            return True
        m = re.search(r'interface\s+\w+(?:<[^{]*?>)?\s+extends\s+([^{]+)\{', src)
        if not m:
            return False
        pkg = fq_internal.rsplit('/', 1)[0]
        for sup in m.group(1).split(','):
            sup = re.sub(r'<.*', '', sup).strip()
            if not sup:
                continue
            cand = sup.replace('.', '/') if '.' in sup else pkg + '/' + sup
            if has_abstract(cand, seen):
                return True
        return False

    def attachable(i):
        """생성 클래스가 붙일 인터페이스: 있는 형이고, 엔티티 API(손 사슬·기본 몸통이 채운다)이거나 몸통 없는 메서드가 없는 것."""
        return available(i) and not has_abstract(i)

    def api_ancestors(fq_internal):
        dotted = fq_internal.replace('/', '.')
        return [dotted] + ancestors(dotted)

    # 만들 클래스: 종류 표의 Craft 클래스와 그 상위 사슬 중 손 클래스가 아닌 것
    plan = {}
    leaves = []
    for t, iface, craft in types:
        if t in SKIP_TYPES:
            continue
        name = 'org/bukkit/craftbukkit/entity/' + craft
        leaves.append((t, craft, name))
        x = name
        while x and x in lock and x not in hand_ctor and x != 'java/lang/Object':
            plan[x] = lock[x]
            x = lock[x]['sup']
        if x not in hand_ctor:
            raise SystemExit(f'손 클래스에 닿지 않는 사슬: {name} -> {x}')
    # 상위 사슬이 쓰는 Craft 인터페이스(CraftEnemy 등)
    for n in list(plan):
        for i in plan[n]['itf']:
            if i.startswith('org/bukkit/craftbukkit/') and is_interface(i) and not os.path.exists(os.path.join(SRC, i + '.java')):
                plan[i] = lock[i]

    def tameable(cls):
        """cls 가 (Furnace 에서) Tameable 을 구현하나 — 손 클래스는 아니라고 본다(손 클래스는 MIXINS 를 직접 갖는다)."""
        if cls in hand_ctor or cls not in lock:
            return False
        e = lock[cls]
        for i in e['itf']:
            if available(i) and 'org.bukkit.entity.Tameable' in api_ancestors(i):
                return True
        return tameable(e['sup'])

    for name, e in sorted(plan.items()):
        path = os.path.join(SRC, name + '.java')
        if not generated(path):
            continue
        pkg, simple = name.rsplit('/', 1)
        pkg = pkg.replace('/', '.')
        itfs = [i.replace('/', '.').replace('$', '.') for i in e['itf'] if (available(i) if is_interface(name) else attachable(i))]
        if is_interface(name):
            body = (f'{HEADER}\npackage {pkg};\n\n'
                    f'/** Paper 1.21.1 {simple} 와 같은 자리의 형(T-264). */\n'
                    f'public interface {simple}{" extends " + ", ".join(itfs) if itfs else ""} {{\n}}\n')
        else:
            mods = ('public ' if e['acc'] & 0x1 else '') + ('abstract ' if e['acc'] & 0x400 else '') + ('final ' if e['acc'] & 0x10 else '')
            sup = e['sup'].replace('/', '.')
            htype = ctor_type(name)
            extra = ''
            if tameable(name) and not tameable(e['sup']):
                extra = ''.join('\n' + '\n'.join(MIXINS[m]) + '\n' for m in MIXINS)
            kind = next((t for t, craft, n in leaves if n == name), None)
            doc = f'{kind} 의 감싸개(Paper {simple} 이름·상위)' if kind else f'Paper 1.21.1 {simple} 와 같은 자리의 중간 계층(T-264)'
            ctor_mod = 'public' if not (e['acc'] & 0x400) else 'protected'
            body = (f'{HEADER}\npackage {pkg};\n\n'
                    f'/** {doc}. */\n'
                    f'{mods}class {simple} extends {sup}{" implements " + ", ".join(itfs) if itfs else ""} {{\n\n'
                    f'    {ctor_mod} {simple}({htype} handle) {{ super(handle); }}\n{extra}}}\n')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(body)
        written += 1

    with open(os.path.join(SRC, 'org/domi/furnace/compat/paper/CraftAccess.java'), encoding='utf-8') as f:
        craft_access = f.read()
    cases, kind_cases = [], []
    for t, craft, name in leaves:
        htype = ctor_type(name)
        key = 'minecraft:' + t.lower()
        if lock[name]['acc'] & 0x1:
            cases.append(f'            case "{key}" -> h instanceof {htype} x ? new org.bukkit.craftbukkit.entity.{craft}(x) : null;')
            kind_cases.append(f'            case "{key}" -> h instanceof {htype} ? org.bukkit.craftbukkit.entity.{craft}.class : null;')
        else:
            # Paper 에서 패키지 전용 — 밖에서는 CraftAccess 한 곳으로만 만든다(설계 3.1 [검토 3])
            const = re.sub(r'(?<!^)(?=[A-Z])', '_', craft).upper()
            if f' {const} =' not in craft_access or f' new{craft}(' not in craft_access:
                raise SystemExit(f'패키지 전용 {craft}: CraftAccess 에 {const}·new{craft} 를 먼저 둔다')
            cases.append(f'            case "{key}" -> h instanceof {htype} x ? CraftAccess.new{craft}(x) : null;')
            kind_cases.append(f'            case "{key}" -> h instanceof {htype} ? CraftAccess.{const} : null;')

    kinds = (f'{HEADER}\npackage org.domi.furnace.compat.paper;\n\n'
             '/** 엔티티 종류 이름 → Paper 와 같은 형의 감싸개. 표에 없거나 핸들 형이 모자라면 null(호출자가 일반 감싸개를 쓴다). */\n'
             'final class CompatEntityKinds {\n\n'
             '    private CompatEntityKinds() {}\n\n'
             '    /** 이 핸들을 감쌀 클래스(없으면 null). */\n'
             '    static Class<? extends org.bukkit.craftbukkit.entity.CraftEntity> kindOf(org.domi.furnace.api.entity.Entity h) {\n'
             '        return switch (h.type()) {\n' + '\n'.join(kind_cases) + '\n'
             '            default -> null;\n        };\n    }\n\n'
             '    /** kindOf 가 고른 클래스의 새 감싸개(없으면 null). */\n'
             '    static org.bukkit.craftbukkit.entity.CraftEntity create(org.domi.furnace.api.entity.Entity h) {\n'
             '        return switch (h.type()) {\n' + '\n'.join(cases) + '\n'
             '            default -> null;\n        };\n    }\n}\n')
    with open(os.path.join(SRC, 'org/domi/furnace/compat/paper/CompatEntityKinds.java'), 'w', encoding='utf-8') as f:
        f.write(kinds)
    print(f'파일 {written + 1}개 씀, 종류 {len(cases)}개')


if __name__ == '__main__':
    main()
