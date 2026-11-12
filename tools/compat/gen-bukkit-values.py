#!/usr/bin/env python3
"""H4 묶음 C: Bukkit 값 열거형(Sound·Particle·block.Biome·Art·attribute.Attribute·potion.PotionType)을 Furnace 1.21.1 데이터에서 만든다.

  python3 tools/compat/gen-bukkit-values.py

1.21.1 의 Bukkit 은 이 여섯이 모두 enum 이다(1.21.3 부터 일부가 인터페이스로 바뀐다 — 1.21.1 플러그인 바이트코드는 invokevirtual 로 부른다).
상수 이름 규칙: 레지스트리 키를 대문자로 하고 '.' 을 '_' 로(Sound: entity.player.levelup → ENTITY_PLAYER_LEVELUP).
선언 순서(ordinal)는 1.21.1 Bukkit 과 같다(PR #241 검토에서 1.21.1 원문과 기계 대조): Sound 는 이름순, Biome·Attribute 는 아래 표 순서,
Particle·PotionType·Art 는 Bukkit 순서표(레지스트리 순서와 다르다). 키·값은 데이터에서 온다.
데이터가 바뀌면 다시 돌려 커밋한다. 호환 시험(BukkitValueTypesTest)이 어긋남을 잡는다.
"""
import os
import re

here = os.path.dirname(os.path.abspath(__file__))
repo = os.path.join(here, "..", "..")
res = os.path.join(repo, "furnace-server", "src", "main", "resources")
src = os.path.join(repo, "furnace-compat-paper", "src", "main", "java")
HEADER = "// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.\n"


def builtin(registry):
    out = []
    with open(os.path.join(res, "registries", "builtin-1.21.1.txt"), encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if len(p) == 2 and p[0] == registry and p[1].startswith("minecraft:"):
                out.append(p[1][len("minecraft:"):])
    return out


def lines(rel):
    with open(os.path.join(res, rel), encoding="utf-8") as f:
        return [l.strip() for l in f if l.strip() and not l.startswith("#")]


def write(rel, text):
    p = os.path.join(src, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def const(key):
    return key.upper().replace(".", "_")


# ------------------------------------------------------------------ Sound
sounds = sorted(builtin("minecraft:sound_event"), key=const)
body = ",\n".join('    %s("%s")' % (const(k), k) for k in sounds)
write("org/bukkit/Sound.java", HEADER + """// sound_event 레지스트리 1.21.1 의 %d 종. 상수 이름은 키의 '.' 을 '_' 로 바꿔 대문자로 한 것, 순서는 이름순(1.21.1 Bukkit 과 같다).
package org.bukkit;

import java.util.HashMap;
import java.util.Map;

/** 바닐라 소리. Paper 는 adventure Sound.Type 도 구현하지만 Adventure 는 쓸 때만 받아 오므로 여기서는 빼 두었다. */
public enum Sound implements Keyed {
%s;

    private static final Map<NamespacedKey, Sound> BY_KEY = new HashMap<>();

    static {
        for (Sound s : values()) BY_KEY.put(s.key, s);
    }

    private final NamespacedKey key;

    Sound(String key) { this.key = NamespacedKey.minecraft(key); }

    /** @deprecated Paper 처럼 Registry#getKey 를 쓰라는 표시만 있다(값은 같다). */
    @Deprecated
    @Override
    public NamespacedKey getKey() { return key; }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static Sound byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
""" % (len(sounds), body))

# ------------------------------------------------------------------ Particle
particles = [l[len("minecraft:"):] for l in lines("registries/particle_types-1.21.1.txt")]
DATA = {
    "block": "org.bukkit.block.data.BlockData.class", "block_marker": "org.bukkit.block.data.BlockData.class",
    "falling_dust": "org.bukkit.block.data.BlockData.class", "dust_pillar": "org.bukkit.block.data.BlockData.class",
    "dust": "DustOptions.class", "dust_color_transition": "DustTransition.class", "entity_effect": "Color.class",
    "item": "org.bukkit.inventory.ItemStack.class", "vibration": "Vibration.class", "sculk_charge": "Float.class",
    "shriek": "Integer.class",
}
for k in DATA:
    assert k in particles, k
# 1.21.1 Bukkit Particle 의 상수 순서(옛 Bukkit 순서에 새 입자를 이은 것 — 레지스트리 순서와 다르다). ordinal·values() 가 같아야 한다.
PARTICLE_ORDER = """poof explosion explosion_emitter firework bubble splash fishing underwater crit enchanted_hit smoke large_smoke effect
instant_effect entity_effect witch dripping_water dripping_lava angry_villager happy_villager mycelium note portal
enchant flame lava cloud dust item_snowball item_slime heart item block rain elder_guardian dragon_breath end_rod
damage_indicator sweep_attack falling_dust totem_of_undying spit squid_ink bubble_pop current_down bubble_column_up
nautilus dolphin sneeze campfire_cosy_smoke campfire_signal_smoke composter flash falling_lava landing_lava
falling_water dripping_honey falling_honey landing_honey falling_nectar soul_fire_flame ash crimson_spore warped_spore
soul dripping_obsidian_tear falling_obsidian_tear landing_obsidian_tear reverse_portal white_ash dust_color_transition
vibration falling_spore_blossom spore_blossom_air small_flame snowflake dripping_dripstone_lava falling_dripstone_lava
dripping_dripstone_water falling_dripstone_water glow_squid_ink glow wax_on wax_off electric_spark scrape sonic_boom
sculk_soul sculk_charge sculk_charge_pop shriek cherry_leaves egg_crack dust_plume white_smoke gust small_gust
gust_emitter_large gust_emitter_small trial_spawner_detection trial_spawner_detection_ominous vault_connection
infested item_cobweb dust_pillar ominous_spawning raid_omen trial_omen block_marker""".split()
assert sorted(PARTICLE_ORDER) == sorted(particles), set(PARTICLE_ORDER) ^ set(particles)
particles = PARTICLE_ORDER
body = ",\n".join('    %s("%s", %s)' % (const(k), k, DATA.get(k, "Void.class")) for k in particles)
write("org/bukkit/Particle.java", HEADER + """// particle_type 레지스트리 1.21.1 의 %d 종(순서는 1.21.1 Bukkit 과 같다 — 레지스트리 순서가 아니다). 상수 이름은 키를 대문자로 한 것(1.20.5 이후 Bukkit 과 같다).
// 옛 이름(REDSTONE·EXPLOSION_NORMAL …)은 Paper 처럼 클래스를 올릴 때 바꿔 준다(compat.nms.ApiRenames).
package org.bukkit;

import java.util.HashMap;
import java.util.Map;

public enum Particle implements Keyed {
%s;

    private static final Map<NamespacedKey, Particle> BY_KEY = new HashMap<>();

    static {
        for (Particle p : values()) BY_KEY.put(p.key, p);
    }

    private final NamespacedKey key;
    private final Class<?> dataType;

    Particle(String key, Class<?> dataType) {
        this.key = NamespacedKey.minecraft(key);
        this.dataType = dataType;
    }

    /** spawnParticle 의 data 로 받는 형(옵션이 없으면 Void). */
    public Class<?> getDataType() { return dataType; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static Particle byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }

    /** dust 입자의 색과 크기. */
    public static class DustOptions {
        private final Color color;
        private final float size;

        public DustOptions(Color color, float size) {
            if (color == null) throw new IllegalArgumentException("color");
            this.color = color;
            this.size = size;
        }

        public Color getColor() { return color; }

        public float getSize() { return size; }
    }

    /** dust_color_transition 입자의 시작색·끝색과 크기. */
    public static class DustTransition extends DustOptions {
        private final Color toColor;

        public DustTransition(Color fromColor, Color toColor, float size) {
            super(fromColor, size);
            if (toColor == null) throw new IllegalArgumentException("toColor");
            this.toColor = toColor;
        }

        public Color getToColor() { return toColor; }
    }
}
""" % (len(particles), body))

# ------------------------------------------------------------------ Biome
BIOME_ORDER = """ocean plains desert windswept_hills forest taiga swamp mangrove_swamp river nether_wastes the_end frozen_ocean frozen_river
snowy_plains mushroom_fields beach jungle sparse_jungle deep_ocean stony_shore snowy_beach birch_forest dark_forest snowy_taiga
old_growth_pine_taiga windswept_forest savanna savanna_plateau badlands wooded_badlands small_end_islands end_midlands end_highlands
end_barrens warm_ocean lukewarm_ocean cold_ocean deep_lukewarm_ocean deep_cold_ocean deep_frozen_ocean the_void sunflower_plains
windswept_gravelly_hills flower_forest ice_spikes old_growth_birch_forest old_growth_spruce_taiga windswept_savanna eroded_badlands
bamboo_jungle soul_sand_valley crimson_forest warped_forest basalt_deltas dripstone_caves lush_caves deep_dark meadow grove
snowy_slopes frozen_peaks jagged_peaks stony_peaks cherry_grove""".split()
biomes = sorted(n[:-5] for n in os.listdir(os.path.join(res, "worldgen", "1.21.1", "worldgen", "biome")) if n.endswith(".json"))
assert sorted(BIOME_ORDER) == biomes, set(BIOME_ORDER) ^ set(biomes)
body = ",\n".join('    %s("%s")' % (const(k), k) for k in BIOME_ORDER)
write("org/bukkit/block/Biome.java", HEADER + """// worldgen/biome 1.21.1 의 %d 종 + CUSTOM. 순서는 1.21.1 Bukkit 과 같다.
package org.bukkit.block;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum Biome implements Keyed {
%s,
    /** 플러그인·데이터 팩이 더한 바이옴(Bukkit 과 같다 — Registry.BIOME 에는 없다). */
    CUSTOM("custom");

    private static final Map<NamespacedKey, Biome> BY_KEY = new HashMap<>();

    static {
        for (Biome b : values()) if (b != CUSTOM) BY_KEY.put(b.key, b);
    }

    private final NamespacedKey key;

    Biome(String key) { this.key = NamespacedKey.minecraft(key); }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 호환 모듈 전용 — 바닐라 키로 찾기(없으면 null, CUSTOM 은 키로 찾지 않는다). */
    public static Biome byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
""" % (len(BIOME_ORDER), body))

# ------------------------------------------------------------------ Art
# 1.21.1 Bukkit Art 의 상수 순서 = 번호(getId) 순서. 1.21 추가분 번호도 Bukkit 과 같다.
ART_ORDER = """kebab aztec alban aztec2 bomb plant wasteland pool courbet sea sunset creebet wanderer graham match bust stage void
skull_and_roses wither fighters pointer pigscene burning_skull skeleton donkey_kong earth wind water fire baroque
humble meditative prairie_ride unpacked backyard bouquet cavebird changing cotan endboss fern finding lowmist orb
owlemons passage pond sunflowers tides""".split()
sizes = {}
for l in lines("entities/painting_variants-1.21.1.txt"):
    n, w, h = l.split()
    sizes[n] = (int(w), int(h))
assert sorted(ART_ORDER) == sorted(sizes), set(ART_ORDER) ^ set(sizes)
art = ART_ORDER
body = ",\n".join('    %s(%d, "%s", %d, %d)' % (const(k), i, k, sizes[k][0], sizes[k][1]) for i, k in enumerate(art))
write("org/bukkit/Art.java", HEADER + """// painting_variant 1.21.1 의 %d 종. 순서·번호는 1.21.1 Bukkit 과 같다. 크기는 데이터 값.
package org.bukkit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Art implements Keyed {
%s;

    private static final Map<Integer, Art> BY_ID = new HashMap<>();
    private static final Map<String, Art> BY_NAME = new HashMap<>();

    static {
        for (Art a : values()) {
            BY_ID.put(a.id, a);
            BY_NAME.put(a.key.getKey(), a);
            BY_NAME.put(a.name().toLowerCase(Locale.ROOT).replace("_", ""), a);
        }
    }

    private final int id;
    private final NamespacedKey key;
    private final int width;
    private final int height;

    Art(int id, String key, int width, int height) {
        this.id = id;
        this.key = NamespacedKey.minecraft(key);
        this.width = width;
        this.height = height;
    }

    /** 가로 블록 수. */
    public int getBlockWidth() { return width; }

    /** 세로 블록 수. */
    public int getBlockHeight() { return height; }

    /** @deprecated 옛 번호. */
    @Deprecated
    public int getId() { return id; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** @deprecated 옛 번호로 찾기(없으면 null). */
    @Deprecated
    public static Art getById(int id) { return BY_ID.get(id); }

    /** 이름으로 찾기(대소문자·밑줄 무시, 없으면 null). */
    public static Art getByName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        String n = name.toLowerCase(Locale.ROOT);
        Art a = BY_NAME.get(n);
        return a != null ? a : BY_NAME.get(n.replace("_", ""));
    }
}
""" % (len(art), body))

# ------------------------------------------------------------------ Attribute
ATTR_ORDER = """generic.max_health generic.follow_range generic.knockback_resistance generic.movement_speed generic.flying_speed
generic.attack_damage generic.attack_knockback generic.attack_speed generic.armor generic.armor_toughness generic.fall_damage_multiplier
generic.luck generic.max_absorption generic.safe_fall_distance generic.scale generic.step_height generic.gravity generic.jump_strength
generic.burning_time generic.explosion_knockback_resistance generic.movement_efficiency generic.oxygen_bonus generic.water_movement_efficiency
player.block_interaction_range player.entity_interaction_range player.block_break_speed player.mining_efficiency player.sneaking_speed
player.submerged_mining_speed player.sweeping_damage_ratio zombie.spawn_reinforcements""".split()
attrs = builtin("minecraft:attribute")
assert sorted(ATTR_ORDER) == sorted(attrs), set(ATTR_ORDER) ^ set(attrs)
body = ",\n".join('    %s("%s")' % (const(k), k) for k in ATTR_ORDER)
write("org/bukkit/attribute/Attribute.java", HEADER + """// attribute 레지스트리 1.21.1 의 %d 종(1.21.1 Bukkit 이름 GENERIC_·PLAYER_ 접두 그대로). 순서는 1.21.1 Bukkit 과 같다.
package org.bukkit.attribute;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum Attribute implements Keyed {
%s;

    private static final Map<NamespacedKey, Attribute> BY_KEY = new HashMap<>();

    static {
        for (Attribute a : values()) BY_KEY.put(a.key, a);
    }

    private final NamespacedKey key;

    Attribute(String key) { this.key = NamespacedKey.minecraft(key); }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 번역 키(attribute.name.generic.max_health). */
    public String getTranslationKey() { return "attribute.name." + key.getKey(); }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static Attribute byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
""" % (len(ATTR_ORDER), body))

# ------------------------------------------------------------------ PotionType
potions = builtin("minecraft:potion")
table = {}
with open(os.path.join(repo, "furnace-server", "src", "main", "java", "org", "domi", "furnace", "server", "effect", "Potions.java"), encoding="utf-8") as f:
    for m in re.finditer(r'p\("([a-z_]+)"((?:, e\("[a-z_]+", \d+, \d+\))*)\);', f.read()):
        table[m.group(1)] = re.findall(r'e\("([a-z_]+)", (\d+), (\d+)\)', m.group(2))
assert list(table) == potions, (list(table), potions)


def effects_of(k):
    return ", ".join('new PotionEffect(PotionEffectType.%s, %s, %s, false, true, true)' % (e.upper(), d, a) for e, d, a in table[k])


# 1.21.1 Bukkit PotionType 의 상수 순서(TURTLE_MASTER 3종이 LUCK 뒤 — 레지스트리 순서와 다르다).
POTION_ORDER = """water mundane thick awkward night_vision long_night_vision invisibility long_invisibility leaping long_leaping
strong_leaping fire_resistance long_fire_resistance swiftness long_swiftness strong_swiftness slowness long_slowness
strong_slowness water_breathing long_water_breathing healing strong_healing harming strong_harming poison long_poison
strong_poison regeneration long_regeneration strong_regeneration strength long_strength strong_strength weakness
long_weakness luck turtle_master long_turtle_master strong_turtle_master slow_falling long_slow_falling wind_charged
weaving oozing infested""".split()
assert sorted(POTION_ORDER) == sorted(potions), set(POTION_ORDER) ^ set(potions)
body = ",\n".join('    %s("%s", %s, %s, List.of(%s))' % (
    const(k), k,
    "true" if ("strong_" + k) in potions else "false",
    "true" if ("long_" + k) in potions else "false",
    effects_of(k)) for k in POTION_ORDER)
write("org/bukkit/potion/PotionType.java", HEADER + """// potion 레지스트리 1.21.1 의 %d 종(순서는 1.21.1 Bukkit 과 같다). 효과·지속·단계는 서버 effect.Potions(바닐라 표)에서 가져왔다.
// 1.20.5 이후 Bukkit 처럼 강화·연장 판(STRONG_·LONG_)은 따로 있는 상수다. 옛 이름(JUMP·SPEED·REGEN …)은 ApiRenames 가 바꿔 준다.
package org.bukkit.potion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum PotionType implements Keyed {
%s;

    private static final Map<NamespacedKey, PotionType> BY_KEY = new HashMap<>();

    static {
        for (PotionType t : values()) BY_KEY.put(t.key, t);
    }

    private final NamespacedKey key;
    private final boolean upgradeable;
    private final boolean extendable;
    private final List<PotionEffect> effects;

    PotionType(String key, boolean upgradeable, boolean extendable, List<PotionEffect> effects) {
        this.key = NamespacedKey.minecraft(key);
        this.upgradeable = upgradeable;
        this.extendable = extendable;
        this.effects = effects;
    }

    /** @deprecated 첫 효과의 종류(효과가 없으면 null). */
    @Deprecated
    public PotionEffectType getEffectType() { return effects.isEmpty() ? null : effects.get(0).getType(); }

    /** 이 물약이 거는 효과(바꿀 수 없는 목록). */
    public List<PotionEffect> getPotionEffects() { return effects; }

    /** @deprecated 첫 효과가 즉시 효과인가. */
    @Deprecated
    public boolean isInstant() { return !effects.isEmpty() && effects.get(0).getType().isInstant(); }

    /** 강화 판(STRONG_ 이름)이 있는가. */
    public boolean isUpgradeable() { return upgradeable; }

    /** 연장 판(LONG_ 이름)이 있는가. */
    public boolean isExtendable() { return extendable; }

    /** 강화 판이 있으면 2, 아니면 1. */
    public int getMaxLevel() { return upgradeable ? 2 : 1; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** @deprecated 그 효과를 첫 효과로 거는 첫 물약 종류(null 이면 WATER, 없으면 null). */
    @Deprecated
    public static PotionType getByEffect(PotionEffectType effectType) {
        if (effectType == null) return WATER;
        for (PotionType t : values()) if (effectType.equals(t.getEffectType())) return t;
        return null;
    }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static PotionType byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
""" % (len(potions), body))

print("sound", len(sounds), "particle", len(particles), "biome", len(BIOME_ORDER), "art", len(art), "attribute", len(ATTR_ORDER), "potion", len(potions))
