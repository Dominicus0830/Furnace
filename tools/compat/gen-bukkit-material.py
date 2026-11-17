#!/usr/bin/env python3
"""Bukkit Material 열거형(org.bukkit.Material)을 Furnace 의 블록·아이템 레지스트리(1.21.1)에서 만든다.

  python3 tools/compat/gen-bukkit-material.py > furnace-compat-paper/src/main/java/org/bukkit/Material.java

Bukkit 의 Material 상수 이름은 레지스트리 키(minecraft:oak_planks)를 대문자로 바꾼 것(OAK_PLANKS)이라 같은 규칙으로 만든다.
레지스트리가 바뀌면(버전 올림) 다시 돌려 커밋한다. 호환 시험(MaterialRegistryTest)이 둘이 어긋나면 실패한다.
"""
import sys, os, gzip

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

# 블록 성질(Paper CraftBlockType 이 읽는 바닐라 값). 기본 상태 id 는 blocks/1.21.1.txt 둘째 칸.
default_state = {}
with open(os.path.join(root, "blocks/1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            default_state[p[0][len("minecraft:"):]] = int(p[1])
state_bits = {}   # stateflags 넷째 칸: replaceable legacySolid blocksMotion hasBlockEntity hasAnalogOutput redstoneConductor ...
with gzip.open(os.path.join(root, "blocks/stateflags_1.21.1.txt.gz"), "rt", encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            state_bits[int(p[0])] = p[4]
bprops = {}   # blockprops: hasCollision explosionResistance requiresCorrectTool randomlyTicking friction speedFactor jumpFactor canOcclude ignitedByLava ...
with open(os.path.join(root, "blocks/blockprops_1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            bprops[p[0][len("minecraft:"):]] = p[1:]
hardness = {}
with open(os.path.join(root, "blocks/hardness_1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            hardness[p[0][len("minecraft:"):]] = p[1]
ignite = {}   # FireBlock.setFlammable(블록, igniteOdds, burnOdds)
with open(os.path.join(root, "blocks/fire_1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            ignite[p[0]] = int(p[1])
# Fallable 을 구현하는 바닐라 블록 부류(reports/blocks.json 의 type): FallingBlock 하위(모루·색 있는 떨어지는 블록·콘크리트 가루·용 알)·뾰족한 점적석·솔질 블록.
FALLABLE_TYPES = {"minecraft:anvil", "minecraft:colored_falling", "minecraft:concrete_powder", "minecraft:dragon_egg",
                  "minecraft:pointed_dripstone", "minecraft:brushable"}
block_type = {}
with open(os.path.join(root, "blocks/definitions_1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if p and not p[0].startswith("#"):
            block_type[p[0][len("minecraft:"):]] = p[1]
transparent = set()
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "bukkit-transparent-1.21.1.txt"), encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line and not line.startswith("#"):
            transparent.add(line.lower())


def fmt_float(v):
    v = float(v)
    return ("%rF" % v).replace("inf", "Float.POSITIVE_INFINITY")


def block_args(n):
    """플래그(SOLID 1·FLAMMABLE 2·BURNABLE 4·OCCLUDING 8·GRAVITY 16·TRANSPARENT 32·COLLISION 64), 단단함, 폭발 저항, 미끄러움."""
    if n not in default_state:
        return "0, 0F, 0F, 0F"
    bits = state_bits[default_state[n]]
    bp = bprops[n]
    flags = 0
    if bits[2] == "1": flags |= 1          # blocksMotion
    if bp[8] == "1": flags |= 2            # ignitedByLava
    if ignite.get(n, 0) > 0: flags |= 4    # igniteOdds > 0
    if bits[5] == "1": flags |= 8          # isRedstoneConductor
    if block_type.get(n) in FALLABLE_TYPES: flags |= 16
    if n in transparent: flags |= 32
    if bp[0] == "1": flags |= 64           # hasCollision
    return "%d, %s, %s, %s" % (flags, fmt_float(hardness[n]), fmt_float(bp[1]), fmt_float(bp[4]))


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
    lines.append("    %s(%s, %s, %d, %d, %s)" % (n.upper(), "true" if n in bset else "false", "true" if n in iset else "false", st, dur, block_args(n)))
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
    private final int flags;
    private final float hardness, blastResistance, slipperiness;

    private static final int SOLID = 1, FLAMMABLE = 2, BURNABLE = 4, OCCLUDING = 8, GRAVITY = 16, TRANSPARENT = 32, COLLISION = 64;

    Material(boolean block, boolean item, int maxStack, int maxDurability, int flags, float hardness, float blastResistance, float slipperiness) {
        this.block = block;
        this.item = item;
        this.maxStack = maxStack;
        this.maxDurability = maxDurability;
        this.flags = flags;
        this.hardness = hardness;
        this.blastResistance = blastResistance;
        this.slipperiness = slipperiness;
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

    // ---- 블록 성질(Paper 1.21.1: Material → BlockType → CraftBlockType 가 바닐라 블록의 기본 상태를 읽는다). 블록이 아니면 false.

    /** 움직임을 막는가(바닐라 기본 상태 blocksMotion). */
    public boolean isSolid() { return (flags & SOLID) != 0; }

    /** 용암에 불붙는가(기본 상태 ignitedByLava). */
    public boolean isFlammable() { return (flags & FLAMMABLE) != 0; }

    /** 불에 타 없어질 수 있는가(바닐라 FireBlock 의 불붙음 확률이 0 보다 큼). */
    public boolean isBurnable() { return (flags & BURNABLE) != 0; }

    /** 빛·시야를 막는 꽉 찬 블록인가(기본 상태 isRedstoneConductor). */
    public boolean isOccluding() { return (flags & OCCLUDING) != 0; }

    /** 중력을 받는가(바닐라 Fallable 블록: 모래·자갈·콘크리트 가루·모루·용 알·뾰족한 점적석·수상한 모래/자갈). */
    public boolean hasGravity() { return (flags & GRAVITY) != 0; }

    /** 빛을 막지 않는 블록인가 — Paper 도 바닐라 값이 아닌 손으로 관리되는 옛 목록이다(@Deprecated). */
    @Deprecated
    public boolean isTransparent() { return (flags & TRANSPARENT) != 0; }

    /** 충돌 상자가 있는가(Paper isCollidable API: 바닐라 Block.hasCollision). 블록이 아니면 IllegalArgumentException. */
    public boolean isCollidable() {
        if (!block) throw new IllegalArgumentException(this + " isn't a block type");
        return (flags & COLLISION) != 0;
    }

    /** 단단함(부수는 시간의 바탕, -1 = 부술 수 없음). 블록이 아니면 IllegalArgumentException. */
    public float getHardness() {
        if (!block) throw new IllegalArgumentException("The Material is not a block!");
        return hardness;
    }

    /** 폭발 저항. 블록이 아니면 IllegalArgumentException. */
    public float getBlastResistance() {
        if (!block) throw new IllegalArgumentException("The Material is not a block!");
        return blastResistance;
    }

    /** 미끄러움(바닐라 friction, 보통 0.6·얼음 0.98). 블록이 아니면 IllegalArgumentException. */
    public float getSlipperiness() {
        if (!block) throw new IllegalArgumentException("The Material is not a block!");
        return slipperiness;
    }

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
