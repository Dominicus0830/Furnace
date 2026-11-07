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

    // 아래 위치 조건 읽기는 그 칸 청크를 지금 스레드가 가진 경우에만 답한다(아니면 null → 조건은 거짓).

    /** StructureManager.getStructureWithPieceAt(pos, 구조물 집합) 이 유효한가. */
    default Boolean structureWithPieceAt(int x, int y, int z, java.util.function.Predicate<String> structureIds) { return null; }

    /** CampfireBlock.isSmokeyPos. */
    default Boolean smokeyAt(int x, int y, int z) { return null; }

    /** Level.getMaxLocalRawBrightness(pos). */
    default Integer lightAt(int x, int y, int z) { return null; }

    /** Level.canSeeSky(pos). */
    default Boolean canSeeSkyAt(int x, int y, int z) { return null; }

    /** 그 칸 유체 상태 — 블록 이름 자리에 유체 이름(minecraft:water 등), 속성은 level(흐르는 것)·falling. */
    default LootBlockState fluidAt(int x, int y, int z) { return null; }

    /** 그 칸 블록 엔티티의 saveWithFullMetadata(없으면 null). */
    default org.domi.furnace.server.net.io.nbt.NbtCompound blockEntityNbtAt(int x, int y, int z) { return null; }

    /** 명령 저장소(command_storage)의 compound(없으면 null) — copy_custom_data 의 storage 원본. */
    default org.domi.furnace.server.net.io.nbt.NbtCompound storageNbt(String storage) { return null; }

    default String dimension() { return "minecraft:overworld"; }
}
