// 지형지물 배치 다리(T-211). Bukkit·Paper 호환 계층의 NMS {@code ConfiguredFeature#place}·{@code PlacedFeature#place} shim 이
// 서버의 지형 생성 코드(바닐라 1.21.1 이식)를 플러그인이 준 레벨·난수로 돌리는 길이다. 불안정한 내부 다리다(호환 계층 전용).
package org.domi.furnace.api.internal;

import java.util.Random;
import org.domi.furnace.api.world.World;

/**
 * 설정된 지형지물(configured feature)·배치된 지형지물(placed feature)을 이름으로 놓는다.
 *
 * <p>"Now" 메서드는 원점 칸을 가진 region 스레드(전역 빌림 구간 포함)에서만 부를 수 있다. 아니면
 * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}.
 *
 * <p>공통 오류:
 * <ul>
 *   <li>모르는 이름: IllegalArgumentException</li>
 *   <li>아직 구현하지 않은 지형지물이 하위에 있다, 또는 플러그인 레벨의 블록 엔티티 내용을 바꿔야 한다: UnsupportedOperationException</li>
 *   <li>원점 청크가 로드돼 있지 않다, 또는 배치가 로드 안 됨·다른 region·원점에서 2청크 넘는 청크에 닿았다: IllegalStateException
 *       (그 전까지의 쓰기는 이미 {@link LevelSink} 에 갔을 수 있다 — 서버 월드에 직접 놓는 길은 아무것도 바꾸지 않는다)</li>
 *   <li>바이옴 거르기가 든 배치된 지형지물: 바닐라처럼 IllegalStateException("Tried to biome check an unregistered feature, …")</li>
 * </ul>
 */
public interface FeatureInternals {

    /**
     * 플러그인 레벨(바닐라 {@code WorldGenLevel})로 가는 쓰기 통로. 지형 생성 코드가 쓰는 차례 그대로 부른다.
     * 블록 읽기는 서버가 실제 월드 + 받아들인 쓰기로 답하고, 하이트맵·유체는 실제 월드로 답한다(Paper 의 populator 와 같다).
     */
    interface LevelSink {

        /** 바닐라 {@code LevelWriter#setBlock(pos, state, flags)}. 거짓이면 그 쓰기는 없던 것이 된다(다음 읽기에 보이지 않는다). */
        boolean setBlock(int x, int y, int z, int stateId, int flags);

        /** 그 칸에 레벨이 가진 블록 엔티티의 종류 id(예: {@code minecraft:beehive}), 없으면 null — 바닐라 {@code getBlockEntity(pos)} 자리. */
        String blockEntityType(int x, int y, int z);

        /** 바닐라 {@code setBlock(pos, state, flags, maxUpdateDepth)}(인자 4개 — Paper 구조물 조각은 레벨을 감싸 이 판으로 쓴다). */
        default boolean setBlock(int x, int y, int z, int stateId, int flags, int maxUpdateDepth) {
            throw new UnsupportedOperationException("setBlock(pos, state, flags, maxUpdateDepth)");
        }

        /** 바닐라 {@code getSeed()}, throughGetLevel 이면 {@code getLevel().getSeed()}. */
        default long levelSeed(boolean throughGetLevel) { throw new UnsupportedOperationException("getSeed"); }

        /** 엔티티를 만들 때 바닐라가 부르는 {@code getLevel()} — 던지면 그대로 나른다. */
        default void entityLevel() { throw new UnsupportedOperationException("getLevel"); }

        /** 바닐라 {@code addFreshEntity}(id 종류의 새 엔티티, 그 위치). */
        default void addEntity(String entityType, double x, double y, double z) { throw new UnsupportedOperationException("addFreshEntity"); }

        /** 바닐라 {@code blockUpdated(pos, block)}(템플릿 모양 갱신 뒤). */
        default void blockUpdated(int x, int y, int z, int stateId) { throw new UnsupportedOperationException("blockUpdated"); }

        // ---- 구조물 몹 finalizeSpawn 의 world 인자 쪽(T-221). 레벨이 던지면 그대로 나른다.

        /** 바닐라 {@code getCurrentDifficultyAt(pos)}: {난이도 번호(0 평화 · 1 쉬움 · 2 보통 · 3 어려움), 실효 난이도}. */
        default float[] currentDifficultyAt(int x, int y, int z) { throw new UnsupportedOperationException("getCurrentDifficultyAt"); }

        /** 바닐라 {@code getMoonBrightness()}. */
        default float moonBrightness() { throw new UnsupportedOperationException("getMoonBrightness"); }

        /** 바닐라 {@code getBiome(pos)} 의 바이옴 id. */
        default String biomeAt(int x, int y, int z) { throw new UnsupportedOperationException("getBiome"); }

        /**
         * 바닐라 {@code getEntitiesOfClass(Chicken.class, box, ENTITY_NOT_BEING_RIDDEN)}(아기 좀비가 탈 닭 찾기): 빈 목록이면 거짓. 레벨이 닭을 돌려주면
         * 그 엔티티에 태울 길이 없어 {@code UnsupportedNmsCall} 류로 던진다.
         */
        default boolean chickensToRide(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            throw new UnsupportedOperationException("getEntitiesOfClass");
        }
    }

    /** 바닐라 {@code Structure#generate} 결과(서버 쪽 시작 — 호환 계층이 그대로 들고 다닌다). */
    interface StructureStartHandle {

        /** 구조물 id. */
        String structureId();

        /** 시작 청크. */
        int chunkX();

        int chunkZ();

        /** 바닐라 {@code getBoundingBox()}(지형 보정 넓힘 포함): minX, minY, minZ, maxX, maxY, maxZ. */
        int[] boundingBox();
    }

    /**
     * 바닐라 {@code Structure#generate(…, chunkPos, references, heightAccessor, biomeTest)}: 그 월드 생성기로 시작 자리를 찾고, 그 자리 바이옴 id 가 biomeTest 를
     * 지나면 조각을 만든다. 유효하지 않으면(자리 없음·바이옴 거절·조각 없음) null. 아무 스레드(생성기만 쓴다).
     *
     * @throws IllegalArgumentException 모르는 구조물이거나 노이즈 생성기가 없는 월드
     * @throws UnsupportedOperationException 아직 구현하지 않은 구조물이거나 템플릿이 없다
     */
    default StructureStartHandle generateStructure(World world, String id, int chunkX, int chunkZ, java.util.function.Predicate<String> biomeTest) {
        throw new UnsupportedOperationException("generateStructure");
    }

    /** 같은 시작을 같은 플러그인 레벨로 여러 청크에 놓는 동안 그 레벨이 받아들인 쓰기를 든 세션(통로 모드 전용, 레벨마다 하나). */
    default Object newStructureSession() { throw new UnsupportedOperationException("newStructureSession"); }

    /** 세션에 그 레벨이 이미 가진 쓰기 하나를 얹는다(서버 레벨의 나무 기록처럼 레벨 읽기가 돌려줄 칸 — 이번 호출 전에 쌓인 것). */
    default void structureSessionWrite(Object session, int x, int y, int z, int stateId) {
        throw new UnsupportedOperationException("structureSessionWrite");
    }

    /**
     * 바닐라 {@code StructureStart#placeInChunk(level, structureManager, generator, random, chunkBox, chunkPos)}. 청크 칸을 가진 region 스레드(전역 빌림 구간 포함)에서만.
     * sink 가 null 이면 서버 월드에 바로(/place structure 와 같은 반영), 아니면 통로로만 쓴다. levelRandom 은 그 레벨의 {@code getRandom()} 을 뽑을 때마다 부른다.
     *
     * @param box minX, minY, minZ, maxX, maxY, maxZ
     * @throws IllegalStateException 닿은 청크가 로드돼 있지 않거나 다른 region·범위 밖
     * @throws UnsupportedOperationException 플러그인 레벨 블록 엔티티 내용 바꾸기, 플러그인 레벨이 돌려준 닭에 아기 좀비 태우기 등 같은 결과를 낼 수 없는 자리
     */
    default void placeStructureNow(World world, StructureStartHandle start, int[] box, int chunkX, int chunkZ, Random random,
                                   java.util.function.Supplier<Random> levelRandom, Object session, LevelSink sink) {
        throw new UnsupportedOperationException("placeStructureNow");
    }

    /**
     * 서버 레벨의 {@code getCurrentDifficultyAt(pos)}(바닐라 Level — 청크가 없으면 머문 시간 0·달 밝기 0): {난이도 번호, 실효 난이도}.
     * 그 칸을 가진 region 스레드(전역 빌림 구간 포함)에서만.
     */
    default float[] difficultyAtNow(World world, int x, int y, int z) { throw new UnsupportedOperationException("difficultyAtNow"); }

    /** 서버 레벨의 {@code getBiome(pos)}(바닐라 BiomeManager 확대 — 시드 지터 포함) 바이옴 id. 그 칸을 가진 region 스레드에서만. 청크가 없으면 IllegalStateException. */
    default String biomeAtNow(World world, int x, int y, int z) { throw new UnsupportedOperationException("biomeAtNow"); }

    /**
     * 지형지물을 원점에 놓는다. placed 가 참이면 배치된 지형지물 표, 거짓이면 설정된 지형지물 표에서 id 를 찾는다(그 월드가 쓰는 표). sink 가 null 이면 서버 월드에 바로 놓고(바닐라 런타임 배치 — 블록 엔티티·생성 NBT 포함, 쓰기 플래그 그대로),
     * 아니면 쓰기를 sink 로만 보내고 서버 월드는 바꾸지 않는다(생성 NBT·엔티티·유체 예약 틱은 버린다 — Paper 의 populator 와 같다).
     * 난수는 random 에서 뽑을 때마다 그대로 뽑는다(원천 상태도 같이 나아간다).
     *
     * @return 바닐라 {@code place} 의 반환값
     */
    boolean placeNow(World world, boolean placed, String id, int x, int y, int z, Random random, LevelSink sink);
}
