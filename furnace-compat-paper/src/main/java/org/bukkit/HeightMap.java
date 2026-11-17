package org.bukkit;

/** Paper 1.21.1 HeightMap — 바닐라 Heightmap.Types 와 같은 여섯 가지(이름이 Furnace GenerationHeightmap 과 같다). */
public enum HeightMap {
    /** 움직임을 막거나 유체가 있는 가장 높은 블록. */
    MOTION_BLOCKING,
    /** MOTION_BLOCKING 에서 잎을 뺀 것. */
    MOTION_BLOCKING_NO_LEAVES,
    /** 움직임을 막는 가장 높은 블록(유체 무시). */
    OCEAN_FLOOR,
    /** 생성 중에 쓰는 OCEAN_FLOOR. */
    OCEAN_FLOOR_WG,
    /** 공기가 아닌 가장 높은 블록. */
    WORLD_SURFACE,
    /** 생성 중에 쓰는 WORLD_SURFACE. */
    WORLD_SURFACE_WG
}
