// 구조물 배치 높이 훅(ChunkGenerationHooks.baseHeight)이 묻는 높이 지도 — 바닐라 Heightmap.Types 중 구조물 배치가 쓰는 두 가지.
// Bukkit HeightMap 의 WORLD_SURFACE_WG·OCEAN_FLOOR_WG 와 같다(호환 어댑터가 이름으로 잇는다).
package org.domi.furnace.api.worldgen;

public enum GenerationHeightmap {
    /** 공기가 아닌 가장 높은 블록(물·잎 포함). 마을·폐허 포털 등 땅 위 구조물. */
    WORLD_SURFACE_WG,
    /** 움직임을 막는 가장 높은 블록(물 바닥). 바다 유적·난파선 등. */
    OCEAN_FLOOR_WG
}
