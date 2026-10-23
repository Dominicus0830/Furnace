// 전리품 평가가 쓰는 월드 기능 — 난수 시퀀스·날씨·시간·제련·점수판·구조물 지도 등.
package org.domi.furnace.server.loot;

import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public interface LootWorld {

    /** 바닐라 Level.getRandom(). */
    NoiseRandom random();

    /** 바닐라 ServerLevel.getRandomSequence(id). */
    NoiseRandom randomSequence(ResourceId id);

    default long dayTime() { return 0L; }

    default boolean isRaining() { return false; }

    default boolean isThundering() { return false; }

    /** 제련 결과 아이템 id(레시피가 없으면 null). */
    default String smeltingResult(String item) { return null; }

    /** 점수판 값(대상·목표·점수가 없으면 null). target = this/attacker/direct_attacker/attacking_player 또는 "fixed:<이름>". */
    default Integer score(LootContext ctx, String target, String objective) { return null; }

    default Number storageNumber(String storage, String path) { return null; }

    /** exploration_map — 가까운 구조물 블록 좌표 {x, z}(없으면 null). */
    default int[] findNearestMapStructure(String structureTag, double[] origin, int radius, boolean skipExisting) { return null; }

    /** exploration_map — 새 지도 id 를 받고 그 지도의 컴포넌트(map_id·map_decorations 등)를 스택에 쓴다. */
    default LootStack createExplorationMap(int x, int z, int zoom, String decoration) { return null; }

    /** location_check 등 — 그 자리의 블록 상태(모르면 null). */
    default LootBlockState blockAt(int x, int y, int z) { return null; }

    default String biomeAt(int x, int y, int z) { return null; }

    default String dimension() { return "minecraft:overworld"; }
}
