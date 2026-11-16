#!/usr/bin/env python3
"""Bukkit Tag 상수(org.bukkit.Tag)와 GameEvent(org.bukkit.GameEvent)를 Furnace 의 바닐라 1.21.1 태그·레지스트리 목록에서 만든다.

  python3 tools/compat/gen-bukkit-tag.py furnace-compat-paper/src/main/java/org/bukkit

상수 이름 규칙(Paper 1.21.1 Tag 와 같다 — 같은 규칙으로 만든 이름이 Paper 최신 상수와 356개 겹치고 어긋난 것이 없음을 대조했다):
  블록 태그 = 태그 경로 대문자(minecraft:wool → WOOL), 아이템 ITEMS_, 유체 FLUIDS_, 엔티티 종류 ENTITY_TYPES_, 게임 이벤트 GAME_EVENT_(단수).
  경로의 '/' 는 '_' (enchantable/sword → ITEMS_ENCHANTABLE_SWORD).
GameEvent 는 게임 이벤트 레지스트리(registries/builtin-1.21.1.txt) 항목마다 상수 하나.
태그가 바뀌면(버전 올림) 다시 돌려 커밋한다. 호환 시험(TagParityTest)이 이 표와 서버 태그가 어긋나면 실패한다.
"""
import os
import sys

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "furnace-server", "src", "main", "resources", "registries")

# (Tag 의 REGISTRY_ 상수 이름, 값, 바닐라 레지스트리, 상수 이름 앞붙이, 값 클래스)
REGISTRIES = [
    ("REGISTRY_BLOCKS", "blocks", "minecraft:block", "", "Material"),
    ("REGISTRY_ITEMS", "items", "minecraft:item", "ITEMS_", "Material"),
    ("REGISTRY_FLUIDS", "fluids", "minecraft:fluid", "FLUIDS_", "Fluid"),
    ("REGISTRY_ENTITY_TYPES", "entity_types", "minecraft:entity_type", "ENTITY_TYPES_", "EntityType"),
    ("REGISTRY_GAME_EVENTS", "game_events", "minecraft:game_event", "GAME_EVENT_", "GameEvent"),
]


# 1.21.1 Bukkit/Paper Tag 에 있던 별칭·옛 이름 상수(Paper 최신 Tag.java 의 @Deprecated(since≤1.21.1 또는 1.21.4 에서 처음 사용 중지) 표시와 대조 — PR #337 검토 R4).
# 이름 → 같은 객체를 가리킬 대상 상수. 규칙으로 만든 이름은 그대로 두고 별칭만 더한다.
TAG_ALIASES = {
    "CARPETS": "WOOL_CARPETS",
    "PIGLIN_FOOD": "ITEMS_PIGLIN_FOOD",
    "FOX_FOOD": "ITEMS_FOX_FOOD",
    "AXOLOTL_TEMPT_ITEMS": "ITEMS_AXOLOTL_FOOD",
    "ENTITY_TYPES_DEFLECTS_ARROWS": "ENTITY_TYPES_DEFLECTS_PROJECTILES",
    "ITEMS_TOOLS": "ITEMS_BREAKS_DECORATED_POTS",
    "ITEMS_FURNACE_MATERIALS": "ITEMS_STONE_CRAFTING_MATERIALS",
    "DRIPSTONE_REPLACEABLE": "DRIPSTONE_REPLACEABLE_BLOCKS",
    "ITEMS_NOTE_BLOCK_TOP_INSTRUMENTS": "ITEMS_NOTEBLOCK_TOP_INSTRUMENTS",
}

# 사용 중지된 GameEvent 별칭(Paper GameEvent.java @Deprecated(since 1.19~1.20.2), 1.21.1 에도 있었다 — 검토 R5). 이름 → 대상 게임 이벤트 키.
GAME_EVENT_ALIASES = {
    "BLOCK_PRESS": "block_activate", "BLOCK_SWITCH": "block_activate", "BLOCK_UNPRESS": "block_deactivate", "BLOCK_UNSWITCH": "block_deactivate",
    "DISPENSE_FAIL": "block_activate", "PISTON_EXTEND": "block_activate", "PISTON_CONTRACT": "block_deactivate",
    "DRINKING_FINISH": "drink", "ELYTRA_FREE_FALL": "elytra_glide", "ENTITY_DAMAGED": "entity_damage",
    "ENTITY_DYING": "entity_die", "ENTITY_KILLED": "entity_die",
    "ENTITY_ROAR": "entity_action", "ENTITY_SHAKE": "entity_action", "RAVAGER_ROAR": "entity_action", "WOLF_SHAKING": "entity_action",
    "MOB_INTERACT": "entity_interact", "RING_BELL": "block_change", "SHULKER_CLOSE": "container_close", "SHULKER_OPEN": "container_open",
}

# GameEvent.notificationRadius: 기본 16, shriek 32, jukebox_play·jukebox_stop_play 10(서버 GameEvent.java 와 같다 — TagParityTest 가 대조).
def game_event_range(e):
    return 32 if e == "shriek" else 10 if e in ("jukebox_play", "jukebox_stop_play") else 16

# VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT(서버 GameEvent.java 의 표와 같다 — TagParityTest 가 대조). resonate_n 은 n, 없으면 0.
GAME_EVENT_FREQUENCY = {
    "step": 1, "swim": 1, "flap": 1, "projectile_land": 2, "hit_ground": 2, "splash": 2,
    "item_interact_finish": 3, "projectile_shoot": 3, "instrument_play": 3, "entity_action": 4,
    "elytra_glide": 4, "unequip": 4, "entity_dismount": 5, "equip": 5, "entity_interact": 6, "shear": 6,
    "entity_mount": 6, "entity_damage": 7, "drink": 8, "eat": 8, "container_close": 9, "block_close": 9,
    "block_deactivate": 9, "block_detach": 9, "container_open": 10, "block_open": 10, "block_activate": 10,
    "block_attach": 10, "prime_fuse": 10, "note_block_play": 10, "block_change": 11, "block_destroy": 12,
    "fluid_pickup": 12, "block_place": 13, "fluid_place": 13, "entity_place": 14, "lightning_strike": 14,
    "teleport": 14, "entity_die": 15, "explode": 15,
}


def game_event_frequency(e):
    if e.startswith("resonate_"):
        return int(e[len("resonate_"):])
    return GAME_EVENT_FREQUENCY.get(e, 0)


def read_tags():
    tags = {}
    with open(os.path.join(root, "tags-1.21.1.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            p = line.split()
            tags.setdefault(p[0], []).append(p[1][len("minecraft:"):] if p[1].startswith("minecraft:") else p[1])
    return tags


def read_registry(name):
    out = []
    with open(os.path.join(root, "builtin-1.21.1.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            reg, key = line.split()[:2]
            if reg == name and key.startswith("minecraft:"):
                out.append(key[len("minecraft:"):])
    return out


def gen_tag(tags):
    out = ["""// 이 파일은 tools/compat/gen-bukkit-tag.py 가 만든다 — 손으로 고치지 않는다.
// Bukkit Tag: 서버나 데이터 팩이 정한 "같은 종류 묶음". 상수는 바닐라 1.21.1 태그마다 하나(규칙은 생성 스크립트 머리말).
// 구현은 org.domi.furnace.compat.paper.CompatTags — Furnace 의 게임 레지스트리 태그(데이터 팩 리로드 반영)를 그때그때 읽는다.
package org.bukkit;

import java.util.Set;
import org.bukkit.entity.EntityType;

public interface Tag<T extends Keyed> extends Keyed {
"""]
    names = set()
    for const, value, vreg, prefix, cls in REGISTRIES:
        out.append(f'    String {const} = "{value}";\n')
        for t in sorted(tags.get(vreg, [])):
            name = prefix + t.upper().replace("/", "_")
            assert name not in names, name
            names.add(name)
            out.append(f'    Tag<{cls}> {name} = Bukkit.getTag({const}, NamespacedKey.minecraft("{t}"), {cls}.class);\n')
    for alias, target in TAG_ALIASES.items():
        assert target in names, target
        assert alias not in names, alias
        names.add(alias)
        cls = next(c for const, value, vreg, prefix, c in REGISTRIES if any(prefix + t.upper().replace("/", "_") == target for t in tags.get(vreg, [])))
        out.append(f'    /** 옛 이름 — {target} 과 같은 태그. */\n    @Deprecated\n    Tag<{cls}> {alias} = {target};\n')
    out.append("""    /** 이 값이 태그에 들어 있는가. */
    boolean isTagged(T item);

    /** 태그에 든 값 전부(바꿀 수 없다). */
    Set<T> getValues();
}
""")
    return "\n".join(out)


def gen_game_event(events):
    out = ["""// 이 파일은 tools/compat/gen-bukkit-tag.py 가 만든다 — 손으로 고치지 않는다.
// Bukkit GameEvent(1.21.1): 게임 이벤트 레지스트리 항목마다 상수 하나(이름 = 키 대문자).
package org.bukkit;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameEvent implements Keyed {

    private static final Map<NamespacedKey, GameEvent> BY_KEY = new LinkedHashMap<>();
"""]
    for e in events:
        out.append(f'    public static final GameEvent {e.upper()} = register("{e}", {game_event_range(e)}, {game_event_frequency(e)});\n')
    for alias, target in GAME_EVENT_ALIASES.items():
        assert target in events, target
        out.append(f'    /** 옛 이름 — {target.upper()} 과 같다. */\n    @Deprecated\n    public static final GameEvent {alias} = {target.upper()};\n')
    out.append("""    private final NamespacedKey key;
    private final int range;
    private final int vibrationLevel;

    private GameEvent(NamespacedKey key, int range, int vibrationLevel) {
        this.key = key;
        this.range = range;
        this.vibrationLevel = vibrationLevel;
    }

    private static GameEvent register(String path, int range, int vibrationLevel) {
        GameEvent e = new GameEvent(NamespacedKey.minecraft(path), range, vibrationLevel);
        BY_KEY.put(e.key, e);
        return e;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 알림 반경(바닐라 notificationRadius, 블록). */
    public int getRange() { return range; }

    /** 진동 세기 1..15(VibrationSystem 의 빈도, 진동 표에 없으면 0). */
    public int getVibrationLevel() { return vibrationLevel; }

    /** 키의 게임 이벤트(없으면 null). */
    public static GameEvent getByKey(NamespacedKey key) { return BY_KEY.get(key); }

    /** 전부(바꿀 수 없다). */
    public static Collection<GameEvent> values() { return Collections.unmodifiableCollection(BY_KEY.values()); }

    @Override
    public String toString() { return "GameEvent{key=" + key + "}"; }
}
""")
    return "\n".join(out)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("사용법: gen-bukkit-tag.py <org/bukkit 폴더>")
    dest = sys.argv[1]
    with open(os.path.join(dest, "Tag.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(gen_tag(read_tags()))
    with open(os.path.join(dest, "GameEvent.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(gen_game_event(read_registry("minecraft:game_event")))
