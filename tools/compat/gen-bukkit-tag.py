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
        out.append(f'    public static final GameEvent {e.upper()} = register("{e}");\n')
    out.append("""    private final NamespacedKey key;

    private GameEvent(NamespacedKey key) { this.key = key; }

    private static GameEvent register(String path) {
        GameEvent e = new GameEvent(NamespacedKey.minecraft(path));
        BY_KEY.put(e.key, e);
        return e;
    }

    @Override
    public NamespacedKey getKey() { return key; }

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
