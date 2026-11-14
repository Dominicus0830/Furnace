#!/usr/bin/env python3
"""Bukkit Material 열거형(org.bukkit.Material)을 Furnace 의 블록·아이템 레지스트리(1.21.1)에서 만든다.

  python3 tools/compat/gen-bukkit-material.py > furnace-compat-paper/src/main/java/org/bukkit/Material.java

Bukkit 의 Material 상수 이름은 레지스트리 키(minecraft:oak_planks)를 대문자로 바꾼 것(OAK_PLANKS)이라 같은 규칙으로 만든다.
레지스트리가 바뀌면(버전 올림) 다시 돌려 커밋한다. 호환 시험(MaterialRegistryTest)이 둘이 어긋나면 실패한다.
"""
import sys, os

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "furnace-server", "src", "main", "resources")


def read(path):
    names = []
    with open(os.path.join(root, path), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key = line.split()[0]
            if key.startswith("minecraft:"):
                names.append(key[len("minecraft:"):])
    return names


props = {}
with open(os.path.join(root, "items/properties_1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        p = line.split()
        props[p[0][len("minecraft:"):]] = (int(p[1]), int(p[2]))

blocks = read("blocks/1.21.1.txt")
items = read("items/1.21.1.txt")
bset, iset = set(blocks), set(items)
allnames = sorted(bset | iset)

out = []
out.append("""// 이 파일은 tools/compat/gen-bukkit-material.py 가 만든다 — 손으로 고치지 않는다.
// Bukkit Material 과 같은 상수 이름(레지스트리 키를 대문자로). 블록·아이템 레지스트리 1.21.1 의 합집합.
package org.bukkit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Material implements Keyed {""")
lines = []
for n in allnames:
    st, dur = props.get(n, (64, 0))
    lines.append("    %s(%s, %s, %d, %d)" % (n.upper(), "true" if n in bset else "false", "true" if n in iset else "false", st, dur))
out.append(",\n".join(lines) + ";")
out.append("""
    /** Paper 의 옛(1.13 전) 상수 이름 앞붙이. Furnace 의 Material 에는 LEGACY_ 상수가 없다. */
    public static final String LEGACY_PREFIX = "LEGACY_";

    private static final Map<String, Material> BY_KEY = new HashMap<>();

    static {
        for (Material m : values()) BY_KEY.put(m.name().toLowerCase(Locale.ROOT), m);
    }

    private final boolean block;
    private final boolean item;
    private final int maxStack;
    private final int maxDurability;

    Material(boolean block, boolean item, int maxStack, int maxDurability) {
        this.block = block;
        this.item = item;
        this.maxStack = maxStack;
        this.maxDurability = maxDurability;
    }

    /** 한 칸에 쌓는 최대 개수(아이템이 아니면 64). */
    public int getMaxStackSize() { return maxStack; }

    /** 최대 내구도(내구도가 없으면 0). */
    public short getMaxDurability() { return (short) maxDurability; }

    /** 기본 상태의 블록 데이터(블록이 아니면 IllegalArgumentException). */
    public org.bukkit.block.data.BlockData createBlockData() { return org.domi.furnace.compat.paper.CompatBlockData.create(this); }

    /** 속성 문자열("[facing=north]")을 입힌 블록 데이터. */
    public org.bukkit.block.data.BlockData createBlockData(String data) { return org.domi.furnace.compat.paper.CompatBlockData.create(this, data); }

    /** 블록으로 놓을 수 있는 종류인가(블록 레지스트리에 있다). */
    public boolean isBlock() { return block; }

    /** 아이템으로 가질 수 있는 종류인가(아이템 레지스트리에 있다). */
    public boolean isItem() { return item; }

    public boolean isAir() { return this == AIR || this == CAVE_AIR || this == VOID_AIR; }

    /**
     * 옛(1.13 전) 상수인가 — Paper 는 이름이 LEGACY_ 로 시작하는 상수만 참. Furnace 는 LEGACY_ 상수를 두지 않아 늘 false
     * (Paper 의 지금 상수와 같은 답이다).
     */
    public boolean isLegacy() { return false; }

    /** 음반인가(Paper: 아이템이 jukebox_playable 을 가짐). 1.21.1 바닐라에서 그 구성 요소를 가진 아이템은 MUSIC_DISC_* 뿐이다. */
    public boolean isRecord() { return item && name().startsWith("MUSIC_DISC_"); }

    @Override
    public NamespacedKey getKey() { return NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT)); }

    /** 이름("STONE", "minecraft:stone", "stone")으로 찾는다. 없으면 null. */
    public static Material matchMaterial(String name) {
        if (name == null) return null;
        String n = name.strip().toLowerCase(Locale.ROOT);
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        return BY_KEY.get(n.replace(' ', '_'));
    }

    /** 정확한 상수 이름으로만 찾는다(Bukkit 의 getMaterial). 없으면 null. */
    public static Material getMaterial(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}""")
sys.stdout.write("\n".join(out) + "\n")
