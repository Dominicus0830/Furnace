#!/usr/bin/env python3
"""Bukkit 엔티티 인터페이스·CraftBukkit 엔티티 클래스 생성기(T-141 D).

입력: tools/compat/bukkit-entity-hierarchy-1.21.1.txt (Paper 1.21.1 등록표·인터페이스 계층, 이름만).
출력(furnace-compat-paper/src/main/java):
  - org/bukkit/entity/**: 손으로 쓴 것이 없는 인터페이스를 표시 인터페이스(메서드 없음)로. 상위 형식은 org.bukkit.entity 안의 것만.
  - org/bukkit/craftbukkit/entity/Craft*.java: 손으로 쓴 것이 없는 종류별 클래스. 가장 가까운 기존 Craft 계층 칸을 상속하고 그 인터페이스를 구현.
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

# 기존 Craft 계층 칸: 인터페이스 → (Craft 클래스, 생성자가 받는 Furnace 핸들 형)
BASES = [
    ('org.bukkit.entity.Animals', 'CraftAnimals', 'org.domi.furnace.api.entity.Mob'),
    ('org.bukkit.entity.Ageable', 'CraftAgeable', 'org.domi.furnace.api.entity.Mob'),
    ('org.bukkit.entity.Creature', 'CraftCreature', 'org.domi.furnace.api.entity.Mob'),
    ('org.bukkit.entity.Mob', 'CraftMob', 'org.domi.furnace.api.entity.Mob'),
    ('org.bukkit.entity.LivingEntity', 'CraftLivingEntity', 'org.domi.furnace.api.entity.LivingEntity'),
    ('org.bukkit.entity.Projectile', 'CraftProjectile', 'org.domi.furnace.api.entity.Entity'),
    ('org.bukkit.entity.Entity', 'CraftEntity', 'org.domi.furnace.api.entity.Entity'),
]
# 손으로 쓴 인터페이스의 메서드 → 그 인터페이스를 조상으로 둔 생성 Craft 클래스에 넣을 구현
MIXINS = {
    'org.bukkit.entity.Tameable': [
        '    @Override',
        '    public boolean isTamed() { return org.domi.furnace.compat.paper.EntityReads.tamed(furnaceHandle()); }',
    ],
}
# 손으로 만든 종류(생성하지 않고 지금 길을 그대로 쓴다)
SKIP_TYPES = {'PLAYER', 'ITEM', 'ARMOR_STAND', 'PIG', 'UNKNOWN'}


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

    # 2) Craft 클래스와 만들기 표
    cases, kind_cases = [], []
    for t, iface, craft in types:
        if t in SKIP_TYPES:
            continue
        anc = [iface] + ancestors(iface)
        base = next((b for b in BASES if b[0] in anc), None)
        if base is None:
            print('기반 없음', t, iface, file=sys.stderr)
            continue
        _, bcls, htype = base
        path = os.path.join(SRC, 'org/bukkit/craftbukkit/entity', craft + '.java')
        if generated(path):
            extra = ''.join('\n' + '\n'.join(MIXINS[m]) + '\n' for m in MIXINS if m in anc)
            body = (f'{HEADER}\npackage org.bukkit.craftbukkit.entity;\n\n'
                    f'/** {t} 의 감싸개(Paper {craft} 이름). */\n'
                    f'public class {craft} extends {bcls} implements {iface} {{\n\n'
                    f'    public {craft}({htype} handle) {{ super(handle); }}\n{extra}}}\n')
            with open(path, 'w', encoding='utf-8') as f:
                f.write(body)
            written += 1
        key = 'minecraft:' + t.lower()
        cases.append(f'            case "{key}" -> h instanceof {htype} x ? new org.bukkit.craftbukkit.entity.{craft}(x) : null;')
        kind_cases.append(f'            case "{key}" -> h instanceof {htype} ? org.bukkit.craftbukkit.entity.{craft}.class : null;')

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
