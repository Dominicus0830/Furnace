#!/usr/bin/env python3
"""Bukkit EntityType·Enchantment(org.bukkit.entity.EntityType, org.bukkit.enchantments.Enchantment)을 Furnace 레지스트리(1.21.1)에서 만든다.

  python3 tools/compat/gen-bukkit-registries.py

상수 이름은 레지스트리 키(minecraft:armor_stand)를 대문자로 바꾼 것(ARMOR_STAND)이다 — Material 과 같은 규칙(gen-bukkit-material.py).
Bukkit 의 옛 이름(DROPPED_ITEM·PRIMED_TNT·SPLASH_POTION 같은 이전 세대 이름)은 두지 않았다.
레지스트리가 바뀌면 다시 돌려 커밋한다. 호환 시험(RegistryValueTypesTest)이 어긋남을 잡는다.
"""
import os

here = os.path.dirname(os.path.abspath(__file__))
repo = os.path.join(here, "..", "..")
res = os.path.join(repo, "furnace-server", "src", "main", "resources", "registries")
src = os.path.join(repo, "furnace-compat-paper", "src", "main", "java")


def keys_of(path, registry=None):
    out = []
    with open(os.path.join(res, path), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if registry is not None:
                if parts[0] != registry:
                    continue
                key = parts[1]
            else:
                key = parts[0]
            if key.startswith("minecraft:"):
                out.append(key[len("minecraft:"):])
    return sorted(set(out))


def write(rel, text):
    p = os.path.join(src, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


entities = keys_of("entity_types-1.21.1.txt")
lines = ",\n".join("    %s" % n.upper() for n in entities)
write("org/bukkit/entity/EntityType.java", """// 이 파일은 tools/compat/gen-bukkit-registries.py 가 만든다 — 손으로 고치지 않는다.
// 엔티티 종류 레지스트리 1.21.1. 상수 이름은 키를 대문자로 한 것이다(Bukkit 의 옛 별칭은 두지 않았다).
package org.bukkit.entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum EntityType implements Keyed {
%s,
    /** 알 수 없는 종류(Bukkit 과 같은 자리). 키는 없다. */
    UNKNOWN;

    private static final Map<String, EntityType> BY_KEY = new HashMap<>();

    static {
        for (EntityType t : values()) if (t != UNKNOWN) BY_KEY.put(t.name().toLowerCase(Locale.ROOT), t);
    }

    /** 키가 없는 UNKNOWN 은 NullPointerException 이 아니라 IllegalArgumentException(Bukkit 과 같다). */
    @Override
    public NamespacedKey getKey() {
        if (this == UNKNOWN) throw new IllegalArgumentException("UNKNOWN 은 키가 없다");
        return NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT));
    }

    /** "minecraft:zombie"·"zombie"·"ZOMBIE" 로 찾는다. 없으면 null. */
    public static EntityType fromKey(String key) {
        if (key == null) return null;
        String k = key.strip().toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        return BY_KEY.get(k);
    }
}
""" % lines)

ench = keys_of("1.21.1.txt", "minecraft:enchantment")
consts = "\n".join("    public static final Enchantment %s = create(\"%s\");" % (n.upper(), n) for n in ench)
write("org/bukkit/enchantments/Enchantment.java", """// 이 파일은 tools/compat/gen-bukkit-registries.py 가 만든다 — 손으로 고치지 않는다.
// 마법 부여 레지스트리 1.21.1. 상수 이름은 키를 대문자로 한 것이다.
package org.bukkit.enchantments;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

/**
 * 마법 부여 종류. 키(minecraft:sharpness)로 구분한다. 최대 레벨·적용 가능한 아이템은 서버 데이터(데이터 팩이 바꿀 수 있음)에서
 * 오는 값이라 호환 계층이 지어내지 않는다 — getMaxLevel·getStartLevel·canEnchantItem 은 UnsupportedFeature.
 */
public final class Enchantment implements Keyed {

    private static final Map<String, Enchantment> BY_KEY = new LinkedHashMap<>();

%s

    private final NamespacedKey key;

    private Enchantment(NamespacedKey key) { this.key = key; }

    private static Enchantment create(String path) {
        Enchantment e = new Enchantment(NamespacedKey.minecraft(path));
        BY_KEY.put(path, e);
        return e;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 키로 찾는다(minecraft 네임스페이스만). 없으면 null. */
    public static Enchantment getByKey(NamespacedKey key) {
        if (key == null || !NamespacedKey.MINECRAFT.equals(key.getNamespace())) return null;
        return BY_KEY.get(key.getKey());
    }

    /**
     * @deprecated 키 문자열(소문자로 바꿔 읽는다)로 찾는다. null 이면 null. 옛 이름(DAMAGE_ALL)은 Paper 처럼 클래스를 올릴 때 바꾼 다리
     * (compat.nms.ApiRenames)가 먼저 새 이름으로 바꿔 넘긴다.
     */
    @Deprecated
    public static Enchantment getByName(String name) {
        if (name == null) return null;
        return getByKey(NamespacedKey.fromString(name.toLowerCase(Locale.ROOT)));
    }

    /** "minecraft:sharpness"·"sharpness"·"SHARPNESS" 로 찾는다. 없으면 null. */
    public static Enchantment fromKey(String name) {
        if (name == null) return null;
        String k = name.strip().toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        return BY_KEY.get(k);
    }

    /** 모든 마법 부여(키 순). */
    public static Iterable<Enchantment> all() { return Collections.unmodifiableCollection(BY_KEY.values()); }

    public int getMaxLevel() { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#getMaxLevel", "최대 레벨은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    public int getStartLevel() { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#getStartLevel", "시작 레벨은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    public boolean canEnchantItem(org.bukkit.inventory.ItemStack item) { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#canEnchantItem", "적용 가능한 아이템은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    @Override
    public boolean equals(Object o) { return o instanceof Enchantment e && e.key.equals(key); }

    @Override
    public int hashCode() { return key.hashCode(); }

    @Override
    public String toString() { return "Enchantment[" + key + "]"; }
}
""" % consts)
print("entities:", len(entities), "enchantments:", len(ench))
