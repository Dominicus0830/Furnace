#!/usr/bin/env python3
"""Bukkit 블록 상태 인터페이스·CraftBukkit 블록 상태 클래스 생성기(T-141 E2).

입력: tools/compat/bukkit-block-state-hierarchy-1.21.1.txt (Paper 1.21.1 CraftBlockStates 등록표·인터페이스 계층, 이름만).
출력(furnace-compat-paper/src/main/java):
  - 손으로 쓴 것이 없는 인터페이스(org.bukkit.block.Chest·TileState·org.bukkit.loot.Lootable 등)를 표시 인터페이스(메서드 없음)로.
  - org/bukkit/craftbukkit/block/Craft*.java: 추상 칸(CraftBlockEntityState·CraftContainer·CraftLootable·CraftFurnace)과 종류별 클래스.
  - org/domi/furnace/compat/paper/CompatBlockStates.java: 블록 엔티티 종류 이름 → 상태 만들기.
plugin 이 instanceof Chest·Sign·TileState 처럼 형을 가르는 것이 목적이다. 메서드는 필요할 때 손으로 인터페이스에 옮겨 쓴다
(그때는 머리말 줄을 지워 생성기가 덮어쓰지 않게 한다).

쓰기: python3 tools/compat/gen-bukkit-block-state-classes.py  (저장소 뿌리에서)
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'furnace-compat-paper/src/main/java')
DATA = os.path.join(ROOT, 'tools/compat/bukkit-block-state-hierarchy-1.21.1.txt')
HEADER = '// 생성됨: tools/compat/gen-bukkit-block-state-classes.py — 손으로 고치려면 이 줄을 지운다.'
CRAFT_PKG = 'org.bukkit.craftbukkit.block'
BASE = 'org.domi.furnace.compat.paper.CompatBlockState'
CTOR_ARGS = 'String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data'


def path_of(fq):
    return os.path.join(SRC, fq.replace('.', '/') + '.java')


def generated(path):
    if not os.path.exists(path):
        return True
    with open(path, encoding='utf-8') as f:
        return f.readline().startswith(HEADER)


def craft_fq(name):
    return BASE if name == 'CompatBlockState' else f'{CRAFT_PKG}.{name}'


def main():
    abstracts, types, sups = [], [], {}
    for line in open(DATA, encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        p = line.split()
        if p[0] == 'A':
            abstracts.append((p[1], p[2], p[3:]))
        elif p[0] == 'T':
            types.append((p[1], p[2], p[3], p[4]))
        else:
            sups[p[1]] = p[2:]

    written = 0

    def write(path, body):
        nonlocal written
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(body)
        written += 1

    # 1) 표시 인터페이스
    for fq, ss in sorted(sups.items()):
        path = path_of(fq)
        if not generated(path):
            continue
        pkg, simple = fq.rsplit('.', 1)
        ext = ', '.join(s.rsplit('.', 1)[1] if s.rsplit('.', 1)[0] == pkg else s for s in ss)
        write(path, f'{HEADER}\npackage {pkg};\n\n'
                    f'/** Paper 1.21.1 {simple} 의 형(종류 가르기용). 메서드는 아직 없다. */\n'
                    f'public interface {simple}{" extends " + ext if ext else ""} {{\n}}\n')

    # 2) 추상 Craft 칸
    for name, parent, ifaces in abstracts:
        path = path_of(craft_fq(name))
        if generated(path):
            write(path, f'{HEADER}\npackage {CRAFT_PKG};\n\n'
                        f'/** Paper {name} 이름의 추상 칸. */\n'
                        f'public abstract class {name} extends {craft_fq(parent)} implements {", ".join(ifaces)} {{\n\n'
                        f'    protected {name}({CTOR_ARGS}) {{ super(dimension, pos, data); }}\n}}\n')

    # 3) 종류별 클래스와 만들기 표
    done, cases = set(), []
    for be, name, parent, iface in types:
        if name not in done:
            done.add(name)
            path = path_of(craft_fq(name))
            if generated(path):
                kinds = ', '.join(t for t, n, _, _ in types if n == name)
                write(path, f'{HEADER}\npackage {CRAFT_PKG};\n\n'
                            f'/** 블록 엔티티 {kinds} 의 블록 상태(Paper {name} 이름). */\n'
                            f'public class {name} extends {craft_fq(parent)} implements {iface} {{\n\n'
                            f'    public {name}({CTOR_ARGS}) {{ super(dimension, pos, data); }}\n}}\n')
        cases.append(f'            case "minecraft:{be}" -> new {CRAFT_PKG}.{name}(dimension, pos, data);')

    write(os.path.join(SRC, 'org/domi/furnace/compat/paper/CompatBlockStates.java'),
          f'{HEADER}\npackage org.domi.furnace.compat.paper;\n\n'
          '/**\n'
          ' * 블록 엔티티 종류 → Paper 와 같은 형의 블록 상태(Paper CraftBlockStates 의 등록표). 블록 엔티티가 없는 블록·표에 없는 종류(양조대)는\n'
          ' * 일반 {@link CompatBlockState}. dimension 이 null 이면 놓이지 않은 상태.\n'
          ' */\n'
          'final class CompatBlockStates {\n\n'
          '    private CompatBlockStates() {}\n\n'
          f'    static CompatBlockState create({CTOR_ARGS}) {{\n'
          '        String be = Compat.furnace().gameRegistries().blockEntityType(((CompatBlockData) data).furnace().type());\n'
          '        if (be == null) return new CompatBlockState(dimension, pos, data);\n'
          '        return switch (be) {\n' + '\n'.join(cases) + '\n'
          '            default -> new CompatBlockState(dimension, pos, data);\n'
          '        };\n    }\n}\n')
    print(f'파일 {written}개 씀, 블록 엔티티 종류 {len(cases)}개')


if __name__ == '__main__':
    main()
