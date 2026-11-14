package org.bukkit;

import java.util.Locale;

/** 유체 종류(Bukkit 1.21.1). 바닐라 유체 레지스트리의 빈 유체(empty)는 없다 — Bukkit 도 같다. */
public enum Fluid implements Keyed {
    WATER,
    FLOWING_WATER,
    LAVA,
    FLOWING_LAVA;

    private final NamespacedKey key = NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT));

    @Override
    public NamespacedKey getKey() { return key; }
}
