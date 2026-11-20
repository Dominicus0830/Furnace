package org.bukkit.craftbukkit.inventory.trim;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/** Paper CraftTrimPattern: 키마다 한 객체만 만든다({@link org.domi.furnace.compat.paper.TrimRegistry}). */
public final class CraftTrimPattern implements TrimPattern {

    private final NamespacedKey key;

    public CraftTrimPattern(NamespacedKey key) { this.key = key; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 바닐라 값의 번역 키(바닐라 설명이 그 키를 가리킨다). 데이터 팩이 더한 값의 설명은 이 계층이 읽지 못한다. */
    @Override
    public String getTranslationKey() {
        if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
            throw new UnsupportedFeature("TrimPattern#getTranslationKey() of " + key, "the description of a data-pack entry is not readable yet");
        }
        return "trim_pattern." + key.getNamespace() + "." + key.getKey();
    }

    @Override
    public String toString() { return "TrimPattern{" + key + "}"; }
}
