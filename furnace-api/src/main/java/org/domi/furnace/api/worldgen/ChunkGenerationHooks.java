// 월드젠 단계 훅(불만 표 40번). Bukkit ChunkGenerator 의 shouldGenerate*/generate* 와 BlockPopulator 에 해당한다.
// 부트스트랩의 LifecycleEvents.WORLD_GENERATION 에서 키로 등록하고, 월드는 키로 고른다(WorldCreator.generationHooks,
// 기본 세 차원은 furnace.properties worldgen-hooks.overworld|the_nether|the_end).
//
// 규칙
//  모든 메서드는 생성 스레드(여럿이 동시에)에서 불린다. 스레드 안전해야 하고, 서버 API(월드·엔티티·스케줄러)를 부르면 안 된다.
//  같은 청크에 대해 여러 번 불릴 수 있다(높이 조회·구조물 배치 판정·청크 재생성).
//    결과는 (시드, 청크 좌표)만의 함수여야 한다. 아니면 청크 경계가 어긋난다.
//  구조물 배치용 높이(바닐라 getBaseHeight)는 baseHeight 훅을 구현하지 않으면 바닐라 노이즈 단계만으로 판정하고, NOISE·SURFACE 훅을 보지 않는다.
//    바닥 열(바닐라 getBaseColumn. 네더 화석·폐허 포털의 바닥 찾기)은 늘 바닐라다(Paper CustomChunkGenerator 도 getBaseColumn 은 늘 바닐라에 맡긴다).
//  훅이 던진 예외는 기록하고 그 단계의 훅만 건너뛴다(청크 생성은 계속한다).
//  훅이 없는 월드는 바닐라와 똑같다. 훅을 단 월드의 차이는 플러그인이 바꾼 것뿐이다.
package org.domi.furnace.api.worldgen;

public interface ChunkGenerationHooks {

    /**
     * 객체로 단 훅({@code WorldCreator.Builder#generationHooksObject})의 이름 — level.dat 설명서에 남는다(T-217). 기본은 클래스 이름.
     * 키로 등록한 훅에는 쓰지 않는다.
     */
    default String name() { return getClass().getName(); }

    /**
     * 객체로 단 훅에 그 월드의 정보를 준다 — 서버가 생성기에 훅을 걸기 직전 월드 스레드에서 한 번(월드마다). 이미 있는 월드의 시드는 level.dat 것.
     * 키로 등록한 훅에는 부르지 않는다(여러 월드가 같은 훅을 쓸 수 있다).
     */
    default void prepare(BiomeProvider.Context world) {}

    /**
     * 객체로 단 훅의 월드가 서버 월드 목록에 오른 직후(틱을 켜고 청크를 만들기 전) 월드 스레드에서 한 번 — 새 월드든 이미 있는 월드든.
     * 여기서는 서버 API 를 불러도 된다(Bukkit 다리가 기본 BlockPopulator 를 다는 자리, CraftBukkit initWorld).
     *
     * @param levelKey 그 월드의 레벨 키
     */
    default void attached(String levelKey) {}

    /**
     * 새 월드의 고정 스폰(Bukkit {@code ChunkGenerator#getFixedSpawnLocation} 과 같은 자리). null 이면 바닐라 스폰 찾기. 새 월드를 처음 만들 때
     * 한 번, 월드를 목록에 올린 뒤·청크를 만들기 전에 월드 스레드에서 부른다 — 여기서는 서버 API 를 불러도 된다. 다른 월드 이름이면 IllegalStateException.
     *
     * @param levelKey 그 월드의 레벨 키
     */
    default org.domi.furnace.api.world.Location fixedSpawn(String levelKey) { return null; }

    /**
     * 그 청크에서 바닐라 단계를 돌릴지 정한다. false 면 그 단계의 바닐라 처리를 건너뛴다.
     *
     * <ul>
     * <li>NOISE 를 끄면 빈(공기) 청크에서 시작한다.
     * <li>STRUCTURES 를 끄면 그 청크를 시작점으로 하는 구조물이 없다.
     * <li>FEATURES 를 끄면 지형지물만 빠진다(구조물 조각은 그대로 놓인다).
     * <li>DECORATION 을 끄면 구조물 조각과 지형지물이 모두 빠진다(Bukkit shouldGenerateDecorations=false). 켰을 때만 FEATURES 를 묻는다.
     * <li>MOBS 를 끄면 그 청크에 처음 동물을 놓지 않는다.
     * </ul>
     *
     * <p>바닐라를 끈 단계도 아래 훅은 불린다.
     */
    default boolean runVanilla(GenerationStage stage, int chunkX, int chunkZ) { return true; }

    /** NOISE 뒤(바이옴은 정해졌고 표면 규칙 전). 여기서 놓은 돌은 바닐라 표면 규칙을 받는다. */
    default void noise(GenerationChunk chunk) {}

    /** SURFACE 뒤. */
    default void surface(GenerationChunk chunk) {}

    /** CARVERS 뒤. */
    default void carvers(GenerationChunk chunk) {}

    /**
     * 그 청크를 시작점으로 structureId(minecraft:village_plains …) 구조물을 만들지 정한다. 바닐라가 고른 뒤에 묻고, false 면 그 자리에
     * 구조물이 없다(Paper AsyncStructureGenerateEvent 취소와 같다). /locate 는 바닐라처럼 이 판정을 보지 않는다.
     */
    default boolean structure(String structureId, int chunkX, int chunkZ) { return true; }

    /**
     * 구조물 배치용 높이를 바꾼다(바닐라 {@code ChunkGenerator.getBaseHeight}, Bukkit {@code ChunkGenerator.getBaseHeight(WorldInfo, Random, x, z, HeightMap)} 와 같다).
     * 마을·폐허 포털·바다 유적 등이 놓일 높이를 물을 때 불린다.
     *
     * <p>NOISE·SURFACE 훅으로 지형을 크게 바꿨다면 여기서 그 지형의 높이를 돌려줘야 구조물이 떠 있거나 묻히지 않는다.
     * <p>돌려주는 값은 바닐라와 Paper 처럼 <b>첫 빈 칸의 y</b>(맨 위 점유 블록 y + 1)다.
     * <p>바닐라 높이를 쓰려면 {@link java.util.OptionalInt#empty()} 를 돌려준다(기본). Bukkit 처럼 {@link UnsupportedOperationException} 을 던져도 같고,
     * 그러면 이 훅 객체에는 다시 묻지 않는다. 다른 예외는 기록하고 그 질의만 바닐라 높이를 쓴다.
     * <p>바닥 열(getBaseColumn: 네더 화석·폐허 포털의 바닥 찾기)은 이 훅과 무관하게 늘 바닐라다.
     * <p>같은 (x, z) 에 여러 번, 여러 스레드에서 불린다. 결과는 (시드, 좌표)만의 함수여야 하고 가벼워야 한다.
     *
     * @param blockX 월드 블록 x
     * @param blockZ 월드 블록 z
     */
    default java.util.OptionalInt baseHeight(GenerationHeightmap heightmap, int blockX, int blockZ) { return java.util.OptionalInt.empty(); }

    /** 장식에서 그 지형지물(placed feature id, minecraft:ore_diamond …)을 놓을지. false 면 그 지형지물만 건너뛴다. */
    default boolean feature(String placedFeatureId, int originChunkX, int originChunkZ) { return true; }

    /** 그 청크(원점)의 바닐라 장식이 끝난 뒤. 둘레 3×3 청크에 쓸 수 있다(나무가 이웃으로 넘치듯). 청크마다 한 번 불린다. */
    default void decorate(DecorationRegion region) {}
}
