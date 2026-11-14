package org.bukkit;

import java.util.Locale;

/** 유체 종류(Paper 1.21.1). 바닐라 유체 레지스트리 순서대로 빈 유체(EMPTY)가 맨 앞에 있다 — Paper 의 Add-missing-Fluid-type 와 같다. */
public enum Fluid implements Keyed {
    EMPTY,
    WATER,
    FLOWING_WATER,
    LAVA,
    FLOWING_LAVA;

    private final NamespacedKey key = NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT));

    @Override
    public NamespacedKey getKey() { return key; }
}
