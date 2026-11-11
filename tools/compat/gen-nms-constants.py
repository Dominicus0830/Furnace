#!/usr/bin/env python3
# 레지스트리 상수 shim 생성(N0): furnace-server 의 registries/builtin-1.21.1.txt 에서 레지스트리 줄을 읽어
# furnace-compat-paper 의 Blocks.java·Items.java 를, registries/tags-1.21.1.txt 에서 BlockTags.java·ItemTags.java 를 다시 쓴다. 사용: python3 -I tools/compat/gen-nms-constants.py (저장소 루트에서)
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

# 태그 상수: 필드 이름 = 경로 대문자('/' → '_'), 바닐라에서 모양이 다른 두 묶음만 바꾼다(가정 — 필드 이름은 난독화 jar 에 없다).
TAGS = os.path.join(ROOT, 'furnace-server/src/main/resources/registries/tags-1.21.1.txt')
TAG_TARGETS = [
    ('minecraft:block', 'BlockTags', 'Block', 'net.minecraft.world.level.block.Block', 'BLOCK', '블록'),
    ('minecraft:item', 'ItemTags', 'Item', 'net.minecraft.world.item.Item', 'ITEM', '아이템'),
]


# 규칙 밖 이름(PR #223 검토: 검토자 기억과 Paper Tag.java 방증, jar 로는 확인 못 함).
FIELD_EXCEPTIONS = {
    'dripstone_replaceable_blocks': 'DRIPSTONE_REPLACEABLE',
    'noteblock_top_instruments': 'NOTE_BLOCK_TOP_INSTRUMENTS',
}


def field(path):
    if path in FIELD_EXCEPTIONS:
        return FIELD_EXCEPTIONS[path]
    if path.startswith('mineable/'):
        return 'MINEABLE_WITH_' + path.split('/', 1)[1].upper()
    if path.startswith('enchantable/'):
        return path.split('/', 1)[1].upper() + '_ENCHANTABLE'
    return path.replace('/', '_').upper()


tags_by_reg = {}
for line in open(TAGS, encoding='utf-8'):
    if line.startswith('#') or not line.strip():
        continue
    reg, tag = line.split()[:2]
    tags_by_reg.setdefault(reg, []).append(tag)

for reg, cls, typ, imp, key, desc in TAG_TARGETS:
    tags = tags_by_reg[reg]
    out = [
        'package net.minecraft.tags;', '',
        'import net.minecraft.core.registries.Registries;',
        'import net.minecraft.resources.ResourceLocation;',
        f'import {imp};', '',
        '/**',
        f' * 바닐라 {desc} 태그 이름 상수(Mojang 이름 shim, N0). 바닐라 1.21.1 데이터의 {desc} 태그 {len(tags)}개(바닐라 클래스의 TagKey 필드 수와 같다).',
        ' * 필드 이름은 경로 대문자이고 mineable/x → MINEABLE_WITH_X, enchantable/x → X_ENCHANTABLE, 규칙 밖 이름 둘은 생성기 FIELD_EXCEPTIONS(가정: 필드 이름은 난독화 jar 에 없어 확인하지 못했다).',
        ' * 내용은 지금 걸린 태그(데이터 팩 리로드 뒤 값)다. 생성: tools/compat/gen-nms-constants.py — 직접 고치지 말 것.',
        ' */',
        f'public final class {cls} {{', '', f'    private {cls}() {{}}', '',
        f'    private static TagKey<{typ}> create(String path) {{ return TagKey.create(Registries.{key}, ResourceLocation.withDefaultNamespace(path)); }}', '',
    ]
    out += [f'    public static final TagKey<{typ}> {field(t.split(":")[1])} = create("{t.split(":")[1]}");' for t in tags]
    out.append('}')
    path = os.path.join(JAVA, 'net', 'minecraft', 'tags', cls + '.java')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')
    print(cls, len(tags))
