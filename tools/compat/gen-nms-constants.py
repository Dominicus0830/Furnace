#!/usr/bin/env python3
# 레지스트리 상수 shim 생성(N0): furnace-server 의 registries/builtin-1.21.1.txt 에서 레지스트리 줄을 읽어
# furnace-compat-paper 의 Blocks.java·Items.java 를 다시 쓴다. 사용: python3 -I tools/compat/gen-nms-constants.py (저장소 루트에서)
import os

ROOT = os.getcwd()
SRC = os.path.join(ROOT, 'furnace-server/src/main/resources/registries/builtin-1.21.1.txt')
JAVA = os.path.join(ROOT, 'furnace-compat-paper/src/main/java')

TARGETS = [
    # (레지스트리, 패키지, 클래스, 값 타입, 표 클래스, 설명)
    ('minecraft:block', 'net.minecraft.world.level.block', 'Blocks', 'Block', 'NmsBlocks', '블록'),
    ('minecraft:item', 'net.minecraft.world.item', 'Items', 'Item', 'NmsItems', '아이템'),
]

lines_by_reg = {}
for line in open(SRC, encoding='utf-8'):
    if line.startswith('#') or not line.strip():
        continue
    reg, key = line.split()
    lines_by_reg.setdefault(reg, []).append(key)

for reg, pkg, cls, typ, table, desc in TARGETS:
    names = lines_by_reg[reg]
    out = [
        f'package {pkg};', '',
        f'import org.domi.furnace.compat.nms.{table};', '',
        '/**',
        f' * {desc} 상수(Mojang 이름 shim, N0). 바닐라 1.21.1 {desc} 레지스트리 순서 그대로({len(names)}개, 바닐라 클래스의 같은 타입 필드 수와 같다),',
        ' * 필드 이름은 등록 이름 경로의 대문자다(가정: 필드 이름은 난독화 jar 에 없어 매핑 파일로 확인하지 못했다).',
        f' * 값은 {{@link {table}}} 표의 같은 객체라 {{@code ==}} 로 비교된다. 생성: tools/compat/gen-nms-constants.py — 직접 고치지 말 것.',
        ' */',
        f'public final class {cls} {{', '', f'    private {cls}() {{}}', '',
    ]
    out += [f'    public static final {typ} {n.split(":")[1].upper()} = {table}.get("{n}");' for n in names]
    out.append('}')
    path = os.path.join(JAVA, *pkg.split('.'), cls + '.java')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')
    print(cls, len(names))
