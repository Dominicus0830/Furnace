package org.bukkit;

/** 광선 판정이 액체를 어떻게 보는가. */
public enum FluidCollisionMode {
    /** 액체를 무시한다. */
    NEVER,
    /** 원천 블록만 맞는다. */
    SOURCE_ONLY,
    /** 모든 액체가 맞는다. */
    ALWAYS
}
