// 구조물 배치 높이 훅(ChunkGenerationHooks.baseHeight)이 묻는 높이 지도 — 바닐라 Heightmap.Types 중 구조물 배치가 쓰는 두 가지.
// Bukkit HeightMap 의 WORLD_SURFACE_WG·OCEAN_FLOOR_WG 와 같다(호환 어댑터가 이름으로 잇는다).
package org.domi.furnace.api.worldgen;

public enum GenerationHeightmap {
    /** 공기가 아닌 가장 높은 블록(물·잎 포함). 마을·폐허 포털 등 땅 위 구조물. */
    WORLD_SURFACE_WG,
    /** 움직임을 막는 가장 높은 블록(물 바닥). 바다 유적·난파선 등. */
    OCEAN_FLOOR_WG,
    // 아래는 장식 단계 영역의 높이맵({@link DecorationRegion#height}) — baseHeight 훅에는 오지 않는다
    /** 공기가 아닌 가장 높은 블록. */
    WORLD_SURFACE,
    /** 움직임을 막거나 유체가 있는 가장 높은 블록. */
    MOTION_BLOCKING,
    /** MOTION_BLOCKING 에서 잎을 뺀 것. */
    MOTION_BLOCKING_NO_LEAVES,
    /** 움직임을 막는 가장 높은 블록(유체 무시). */
    OCEAN_FLOOR
}
