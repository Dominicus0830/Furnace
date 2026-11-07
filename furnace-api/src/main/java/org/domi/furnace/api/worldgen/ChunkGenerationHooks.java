// 월드젠 단계 훅(불만 표 40번) — Bukkit ChunkGenerator 의 shouldGenerate*/generate* + BlockPopulator 에 해당한다.
// 부트스트랩의 LifecycleEvents.WORLD_GENERATION 에서 키로 등록하고, 월드는 키로 고른다(WorldCreator.generationHooks,
// 기본 세 차원은 furnace.properties worldgen-hooks.overworld|the_nether|the_end).
//
// 규칙
//  - 모든 메서드는 생성 스레드(여럿이 동시에)에서 불린다. 스레드 안전해야 하고, 서버 API(월드·엔티티·스케줄러)를 부르면 안 된다.
//  - 같은 청크에 대해 여러 번 불릴 수 있다(높이 조회·구조물 배치 판정·청크 재생성). 결과는 (시드, 청크 좌표)만의 함수여야 한다 —
//    아니면 청크 경계가 어긋난다.
//  - 훅이 던진 예외는 기록하고 그 단계의 훅만 건너뛴다(청크 생성은 계속).
//  - 훅이 없는 월드는 바닐라와 똑같다. 훅을 단 월드의 차이는 플러그인이 바꾼 것뿐이다.
package org.domi.furnace.api.worldgen;

public interface ChunkGenerationHooks {

    /**
     * 그 청크에서 바닐라 단계를 돌릴지. false 면 그 단계 바닐라 처리를 건너뛴다 — NOISE 를 끄면 빈(공기) 청크에서 시작하고,
     * STRUCTURES 를 끄면 그 청크를 시작점으로 하는 구조물이 없고, FEATURES 를 끄면 지형지물만 빠진다(구조물 조각은 그대로 놓인다).
     * 바닐라를 끈 단계도 아래 훅은 불린다.
     */
    default boolean runVanilla(GenerationStage stage, int chunkX, int chunkZ) { return true; }

    /** NOISE 뒤(바이옴은 정해졌고 표면 규칙 전). 여기서 놓은 돌은 바닐라 표면 규칙을 받는다. */
    default void noise(GenerationChunk chunk) {}

    /** SURFACE 뒤. */
    default void surface(GenerationChunk chunk) {}

    /** CARVERS 뒤. */
    default void carvers(GenerationChunk chunk) {}

    /**
     * 그 청크를 시작점으로 structureId(minecraft:village_plains …) 구조물을 만들지. 바닐라가 고른 뒤에 묻는다 — false 면 그 자리에
     * 구조물이 없다(Paper AsyncStructureGenerateEvent 취소와 같다). /locate 는 바닐라처럼 이 판정을 보지 않는다.
     */
    default boolean structure(String structureId, int chunkX, int chunkZ) { return true; }

    /** 장식에서 그 지형지물(placed feature id, minecraft:ore_diamond …)을 놓을지. false 면 그 지형지물만 건너뛴다. */
    default boolean feature(String placedFeatureId, int originChunkX, int originChunkZ) { return true; }

    /** 그 청크(원점)의 바닐라 장식이 끝난 뒤. 둘레 3×3 청크에 쓸 수 있다(나무가 이웃으로 넘치듯). 청크마다 한 번 불린다. */
    default void decorate(DecorationRegion region) {}
}
