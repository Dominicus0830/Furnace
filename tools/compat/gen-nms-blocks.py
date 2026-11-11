#!/usr/bin/env python3
# Blocks shim 생성(N0): furnace-server 의 registries/builtin-1.21.1.txt 에서 minecraft:block 줄을 읽어
# furnace-compat-paper 의 net/minecraft/world/level/block/Blocks.java 를 다시 쓴다. 사용: python3 -I tools/compat/gen-nms-blocks.py (저장소 루트에서)
import os

ROOT = os.getcwd()
SRC = os.path.join(ROOT, 'furnace-server/src/main/resources/registries/builtin-1.21.1.txt')
OUT = os.path.join(ROOT, 'furnace-compat-paper/src/main/java/net/minecraft/world/level/block/Blocks.java')

names = [line.split()[1] for line in open(SRC, encoding='utf-8') if line.startswith('minecraft:block ')]
lines = [
    'package net.minecraft.world.level.block;', '',
    'import org.domi.furnace.compat.nms.NmsBlocks;', '',
    '/**',
    ' * 블록 상수(Mojang 이름 shim, N0). 바닐라 1.21.1 블록 레지스트리 순서 그대로, 필드 이름은 등록 이름 경로의 대문자다(가정: 1.21.1 Blocks 필드 이름이',
    ' * 모두 이 규칙을 따른다고 보았다 — 매핑 파일로 확인하지 못했다). 값은 {@link NmsBlocks} 표의 같은 객체라 {@code ==} 로 비교된다.',
    ' * 생성: tools/compat/gen-nms-blocks.py — 직접 고치지 말 것.',
    ' */',
    'public final class Blocks {', '', '    private Blocks() {}', '',
]
lines += [f'    public static final Block {n.split(":")[1].upper()} = NmsBlocks.get("{n}");' for n in names]
lines.append('}')
with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print(len(names), 'blocks')
