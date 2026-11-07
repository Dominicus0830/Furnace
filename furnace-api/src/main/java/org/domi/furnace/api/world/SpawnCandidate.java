// 자연 스폰 한 번의 후보(몹 종류·종류 묶음·칸) — SpawnFilter 가 받는다. 불변.
package org.domi.furnace.api.world;

import java.util.UUID;
import org.domi.furnace.api.worldgen.SpawnCategory;

/**
 * @param type          몹 종류 키(minecraft:zombie)
 * @param category      자연 스폰 종류 묶음(MobCategory)
 * @param dimension     차원 키(minecraft:overworld)
 * @param biome         그 칸의 바이옴 키(모르면 null)
 * @param nearestPlayer 가장 가까운 플레이어(바닐라가 거리 검사에 쓰는 사람 — 관전자 제외)
 */
public record SpawnCandidate(String type, SpawnCategory category, String dimension, int x, int y, int z, String biome, UUID nearestPlayer) {

    public BlockPos pos() { return new BlockPos(x, y, z); }
}
