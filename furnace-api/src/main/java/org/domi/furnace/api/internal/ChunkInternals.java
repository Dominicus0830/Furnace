// 청크 내부 읽기 다리(N2a). Bukkit·Paper 호환 계층의 NMS 청크 shim(LevelChunk·LevelChunkSection·PalettedContainer·Heightmap)이
// 서버 청크를 "보기"로 읽는 길이다. 블록 상태 번호(BlockTypes.stateId 와 같은 번호)와 바이옴 레지스트리 번호 단위로 다룬다.
//
// 불안정한 내부 다리다(Internals 와 같은 등급, 호환 계층 전용). 일반 플러그인은 World·Block API 를 쓴다.
// 쓰기는 두 가지다.
//   단일 칸 쓰기 둘: 바닐라 Level.setBlock(플래그·깊이 그대로)과 LevelChunk.setBlockState
//   N2b-2 의 주인 스레드 원시 쓰기: 섹션 칸, 바이옴 칸, 바닐라 개수, 하이트맵
package org.domi.furnace.api.internal;

import java.util.List;
import org.domi.furnace.api.world.World;

/**
 * 청크 섹션·하이트맵·바이옴의 번호 단위 읽기.
 *
 * <p>"Now" 가 붙은 메서드는 그 청크를 가진 region 스레드(전역 빌림 구간 포함)에서만 부를 수 있다.
 * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다.
 *
 * <p>청크가 로드돼 있지 않으면 표시값(null·-1·{@link Integer#MIN_VALUE})을 돌려줄 뿐, 청크를 만들거나 불러오지 않는다.
 * 섹션 번호는 0 이 맨 아래 섹션이다(바닐라 getSectionIndex).
 */
public interface ChunkInternals {

    /** 그 차원의 가장 낮은 블록 y(바닐라 getMinBuildHeight). 아무 스레드. */
    int minY(World world);

    /** 그 차원의 높이(블록 수, 바닐라 getHeight). 아무 스레드. */
    int height(World world);

    /** 지금 로드돼 있는가(최근 값). 아무 스레드. */
    boolean isLoaded(World world, int chunkX, int chunkZ);

    /** 지금 스레드가 그 청크를 가진 region 스레드인가(전역 빌림 구간이면 빌려 본다). 아무 스레드. */
    boolean ownsChunk(World world, int chunkX, int chunkZ);

    /** 그 칸의 블록 상태 번호. 높이 밖은 공기(0)를 돌려주지 않으므로 요청하지 말 것. 높이 밖은 부르는 쪽이 거른다. 로드돼 있지 않으면 -1. */
    int stateIdNow(World world, int x, int y, int z);

    /** 섹션의 블록 상태 번호 4096개(YZX, 새 배열). 로드돼 있지 않으면 null. */
    int[] sectionStatesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * T-209: 섹션 블록 저장소의 사본({@link PackedStates}, 서버 배치 그대로). 주인 스레드 전용. 로드돼 있지 않거나 펼친 배열이 열려 있으면 null —
     * 그때는 {@link #sectionStatesNow}.
     */
    default PackedStates sectionStatesPackedNow(World world, int chunkX, int sectionIndex, int chunkZ) { return null; }

    /**
     * T-209: 섹션 블록 저장소(팔레트 + 묶인 칸 배열)를 서버 배치 그대로 — 팔레트 항목 순서·쓰이지 않는 옛 항목·비트 수까지. 서버 저장소는 바닐라
     * {@code PalettedContainer} 포팅이라(단일 값 0 비트 → 간접 4~8 비트 → 직접, 묶는 배치 같음) 바닐라가 읽을 수 있는 꼴이다 — 단 비트가 커질 때 바닐라는
     * 칸 순서대로 다시 매겨 옛 항목을 버리고(copyFrom) 서버는 옛 순서·옛 항목을 그대로 두어 팔레트가 다를 수 있다(칸 값은 같다). 호환 계층이 칸 4096개를 펼치지 않고
     * {@code PalettedContainer.data} 를 만든다. 칸 순서는 YZX, 묶는 배치는 바닐라 SimpleBitStorage 와 같다(한 long 에 64/bits 개, long 경계를 넘지 않음).
     * 불변 사본이라 아무 스레드나 읽는다.
     */
    interface PackedStates {
        /** 칸당 비트 수. 0 이면 모든 칸이 {@code paletteValue(0)}. */
        int bits();

        /** 팔레트 항목 수(쓰이지 않는 옛 항목 포함 — 같은 비트 수 안에서는 바닐라처럼 줄지 않고, 비트가 커져도 서버는 옛 항목을 남긴다). 직접 저장(칸에 상태 번호 그대로)이면 -1, 0 비트면 1. */
        int paletteSize();

        /** 팔레트 i 번째 상태 번호(i &lt; paletteSize()). */
        int paletteValue(int i);

        /** 묶인 칸 배열의 새 사본(부른 쪽이 고쳐도 된다). 0 비트면 빈 배열. */
        long[] rawCopy();
    }

    /**
     * 섹션의 공기 아닌 칸 수(air·cave_air·void_air 는 공기). 0 인지(바닐라 hasOnlyAir)는 바닐라와 같다.
     * 다만 값은 바닐라 nonEmptyBlockCount 와 다를 수 있다(바닐라는 유체 있는 칸을 한 번 더 센다). 로드돼 있지 않으면 -1.
     */
    int sectionNonAirNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션에 무작위 틱 받는 칸(블록 또는 그 유체)이 있는가(바닐라 isRandomlyTicking). 로드돼 있지 않으면 false. */
    boolean sectionRandomlyTickingNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 섹션의 바닐라 무작위 틱 판정을 비트로 나눠 돌려준다. 비트 1 은 isRandomlyTickingBlocks(tickingBlockCount > 0),
     * 비트 2 는 isRandomlyTickingFluids(tickingFluidCount > 0)다. 칸 쓰기로 놓은 물도 센다(바닐라 그대로). 로드돼 있지 않으면 0.
     */
    int sectionRandomTickingPartsNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션의 바이옴 레지스트리 번호 64개(4×4×4, YZX). 로드돼 있지 않으면 null. */
    int[] sectionBiomesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 그 블록 상태가 무작위 틱을 받는가(바닐라 BlockState.isRandomlyTicking). 블록 자체만 보고 유체 쪽은 제외한다. 모르는 번호면 false. 아무 스레드(고정 표).
     */
    boolean stateRandomlyTicking(int stateId);

    /**
     * 로드된 청크의 "이번 로드"를 나타내는 불투명 객체. 같은 로드 동안에는 같은 객체이고, 내렸다 다시 올리면 다른 객체다. 로드돼 있지 않으면 null. 아무 스레드.
     *
     * <p>호환 계층이 청크 shim 의 정체성을 바닐라처럼 로드 동안 하나로 묶는 데 쓴다(N2b). 청크 객체가 아닌 전용 표시라서 쥐고 있어도 청크를 붙잡지 않는다.
     * 청크를 재생성해도 표시는 이어받는다(Paper 는 재생성해도 같은 LevelChunk 다).
     */
    Object loadedChunkToken(World world, int chunkX, int chunkZ);

    /** 바이옴 레지스트리의 키 목록(번호 순, 데이터 팩 바이옴 포함). 아무 스레드. */
    List<String> biomeKeys();

    /**
     * 바닐라 Level#getUncachedNoiseBiome: 월드 생성기가 그 quart 칸에 정하는 바이옴 번호({@link #biomeKeys()} 순 — 청크에 저장된 값이 아니다).
     * 아무 스레드. 바이옴 원천이 없는 생성기면 UnsupportedOperationException.
     */
    default int noiseBiome(World world, int quartX, int quartY, int quartZ) { throw new UnsupportedOperationException("noiseBiome"); }

    /**
     * 하이트맵의 첫 빈 칸 y(바닐라 Heightmap.getFirstAvailable, 곧 가장 높은 해당 블록 y + 1).
     * heightmap 은 바닐라 직렬화 키(WORLD_SURFACE·OCEAN_FLOOR·MOTION_BLOCKING·MOTION_BLOCKING_NO_LEAVES)다.
     * 로드돼 있지 않으면 {@link Integer#MIN_VALUE}, 모르는 키면 IllegalArgumentException.
     */
    int firstAvailableNow(World world, String heightmap, int x, int z);

    /**
     * 청크 홀더의 단계(바닐라 LevelChunk.getFullStatus — 티켓 레벨): 0 INACCESSIBLE, 1 FULL, 2 BLOCK_TICKING, 3 ENTITY_TICKING. 로드돼 있지 않으면 -1.
     * 바닐라 티켓 규칙의 값이다(Paper 는 플레이어 티켓을 시뮬레이션 거리로 걸어 다를 수 있다 — T-204 설계 4장).
     */
    int fullChunkStatusNow(World world, int chunkX, int chunkZ);

    /** 청크를 저장 대상으로 표시한다(바닐라 setUnsaved(true)). 로드돼 있지 않으면 false. */
    boolean markUnsavedNow(World world, int chunkX, int chunkZ);

    /**
     * 바닐라 Level.setBlock(pos, state, flags, maxUpdateDepth) 그대로 동작한다.
     * 플래그 1·2·16·32·64 와 남은 깊이를 따르고, 클라이언트 알림은 틱 끝에 모아 보내며, 길찾기·POI 갱신까지 한다.
     * 바뀌었으면 true, 높이 밖이거나 로드돼 있지 않거나 같은 상태면 false.
     */
    boolean setBlockNow(World world, int x, int y, int z, int stateId, int flags, int maxUpdateDepth);

    /**
     * 바닐라 LevelChunk.setBlockState 그대로 동작한다. 그 칸만 바꾸고 놓기·치우기 반응, 블록 엔티티, 빛, 하이트맵, 저장 표시까지 처리한다.
     * 이웃·모양 갱신, 클라이언트 알림, POI 는 하지 않는다.
     * 옛 상태 번호를 돌려준다. 바뀌지 않았거나(같은 상태, 공기뿐인 섹션에 공기), 치우기 반응이 칸을 갈아 치웠거나, 높이 밖이거나, 로드돼 있지 않으면 -1.
     */
    int setBlockStateNow(World world, int x, int y, int z, int stateId, boolean moved);

    /**
     * 쪼갠 Level.setBlock 조각(T-204, 주인 스레드) — {@link #blockEffectNow} 의 종류. a·b·c·other 의 뜻은 종류마다 다르다(상태는 바닐라 상태 번호, 블록은
     * 그 블록의 아무 상태 번호):
     * <ul>
     *   <li>SEND_BLOCK_UPDATED(a 옛 상태, b 새 상태): ServerLevel.sendBlockUpdated — 변경 방송 + 충돌 모양이 바뀌면 길찾기 다시</li>
     *   <li>BLOCK_CHANGED: ServerChunkCache.blockChanged — 변경 방송만</li>
     *   <li>UPDATE_NEIGHBORS(a 블록): Level.blockUpdated·updateNeighborsAt — 갱신기(플러그인 BlockPhysicsEvent 포함)</li>
     *   <li>OUTPUT_SIGNAL(a 블록): Level.updateNeighbourForOutputSignal</li>
     *   <li>POI(a 옛 상태, b 새 상태): ServerLevel.onBlockStateChange</li>
     *   <li>ON_PLACE(a 상태, b 옛 상태, c 피스톤 1/0): BlockState.onPlace</li>
     *   <li>NEIGHBOR_CHANGED(a 상태, b 원천 블록, c 피스톤, other 원천 칸 BlockPos.asLong): BlockState.handleNeighborChanged — 갱신기를 거치지 않는다</li>
     *   <li>NEIGHBOUR_SHAPES·INDIRECT_SHAPES(a 상태, b 플래그, c 남은 깊이): BlockState.updateNeighbourShapes·updateIndirectNeighbourShapes</li>
     *   <li>TRIGGER_EVENT(a 상태, b 종류, c 값): BlockState.triggerEvent — 1/0</li>
     *   <li>FROM_NEIGHBOUR_SHAPES(a 상태): Block.updateFromNeighbourShapes — 새 상태</li>
     *   <li>SCHEDULE_BLOCK_TICK(a 블록, b 지연, c 우선순위 -3..3): LevelAccessor.scheduleTick(pos, Block, …) — 그 칸 청크가 없으면 순번만 쓰고 버린다(바닐라)</li>
     *   <li>SCHEDULE_FLUID_TICK(a 유체 종류 0..4 — 빈 것·흐르는 물·물·흐르는 용암·용암, b 지연, c 우선순위): LevelAccessor.scheduleTick(pos, Fluid, …)</li>
     * </ul>
     */
    int EFFECT_SEND_BLOCK_UPDATED = 0, EFFECT_BLOCK_CHANGED = 1, EFFECT_UPDATE_NEIGHBORS = 2, EFFECT_OUTPUT_SIGNAL = 3, EFFECT_POI = 4,
            EFFECT_ON_PLACE = 5, EFFECT_NEIGHBOR_CHANGED = 6, EFFECT_NEIGHBOUR_SHAPES = 7, EFFECT_INDIRECT_SHAPES = 8, EFFECT_TRIGGER_EVENT = 9,
            EFFECT_FROM_NEIGHBOUR_SHAPES = 10, EFFECT_SCHEDULE_BLOCK_TICK = 11, EFFECT_SCHEDULE_FLUID_TICK = 12;

    /**
     * 그 칸의 주인 스레드에서 쪼갠 Level.setBlock 조각 하나를 서버의 같은 코드로 한다(T-204). 원점 칸의 청크가 로드돼 있지 않으면 -1, 값이 있는 종류는
     * 그 값, 나머지는 0. 소유 스레드가 아니면 ThreadOwnershipException.
     */
    int blockEffectNow(World world, int kind, int x, int y, int z, int a, int b, int c, long other);

    /** Paper LevelChunk.setBlockState(pos, state, moved, doPlace): doPlace 가 거짓이면 놓기 반응(onPlace)을 건너뛴다. 그 밖은 {@link #setBlockStateNow}. */
    int setBlockStateNow(World world, int x, int y, int z, int stateId, boolean moved, boolean doPlace);

    /** 그 월드 차원의 바닐라 dimensionType().ultraWarm()(네더 참). 고정 값 — 아무 스레드. */
    boolean ultraWarm(World world);

    /** 바닐라 WorldBorder.isWithinBounds(x, z) — 그 월드의 경계(지금 값). 아무 스레드. */
    boolean withinBorder(World world, double x, double z);

    // ------------------------------------------------------------------ 놓기 상태 계산(T-204 3단계 — FAWE PlacementStateProcessor 등)

    /**
     * 놓기 계산이 읽는 레벨. 호환 계층이 NMS 레벨 객체의 가상 메서드로 답한다(플러그인이 재정의했으면 그것 — FAWE 의 편집 중 내용). 부른 스레드에서 읽는다.
     * {@link #serverWorld()} 가 null 이 아니면 서버 자신의 월드라 서버가 그 월드를 직접 읽는다(나머지 메서드는 쓰지 않는다 — 그 칸의 주인 스레드여야 한다).
     */
    interface PlacementLevel {
        /** 그 칸의 상태 번호. */
        int blockStateAt(int x, int y, int z);

        /** 그 칸의 유체: 종류(0 빈 것, 1 흐르는 물, 2 물, 3 흐르는 용암, 4 용암) | 양(1..8, 원천 8) &lt;&lt; 3 | 떨어짐 &lt;&lt; 7. */
        int fluidStateAt(int x, int y, int z);

        /** 그 칸의 블록 엔티티 저장 꼴(saveWithoutMetadata — 없으면 null). 서버는 그 칸 상태로 떨어진 블록 엔티티를 만들어 싣는다(FAWE 프록시와 같다). */
        org.domi.furnace.api.nbt.NbtCompound blockEntityAt(int x, int y, int z);

        int minBuildHeight();

        /** 상한(제외). */
        int maxBuildHeight();

        /** getWorldBorder().isWithinBounds(pos). */
        boolean withinBorder(int x, int y, int z);

        /** 서버 자신의 월드면 그 월드, 아니면 null. */
        default World serverWorld() { return null; }
    }

    /**
     * 바닐라 BlockPlaceContext 의 값(방향은 바닐라 3D 번호: 아래 0, 위 1, 북 2, 남 3, 서 4, 동 5). 가상 메서드로 답하므로 부를 때마다 다시 묻는다.
     * 놓는 플레이어가 있는 문맥은 아직 없다(호환 계층이 UnsupportedNmsCall).
     */
    interface PlacementContext {
        PlacementLevel level();

        /** getClickedPos — {x, y, z}. */
        int[] clickedPos();

        int clickedFace();

        /** getClickLocation — {x, y, z}. */
        double[] clickLocation();

        boolean isInside();

        /** getItemInHand(빈 것은 ItemStack.EMPTY). */
        org.domi.furnace.api.item.ItemStack itemInHand();

        /** getHand: 0 주 손, 1 보조 손(null 이면 0). */
        int hand();

        boolean replacingClickedOnBlock();

        boolean canPlace();

        int nearestLookingDirection();

        int nearestLookingVerticalDirection();

        int[] nearestLookingDirections();

        int horizontalDirection();

        boolean isSecondaryUseActive();

        float rotation();
    }

    /** Block.getStateForPlacement(ctx): 서버 블록 동작의 같은 메서드. block 은 그 블록의 아무 상태 번호. null 이면 -1. 부른 스레드에서. */
    int stateForPlacement(int block, PlacementContext ctx);

    /** BlockState.canBeReplaced(ctx). */
    boolean canBeReplaced(int state, PlacementContext ctx);

    /** BlockState.canSurvive(level, pos). */
    boolean canSurvive(int state, PlacementLevel level, int x, int y, int z);

    // ------------------------------------------------------------------ 원시 쓰기(N2b-2): 바닐라처럼 그 값만 바꾸고 부수 효과가 없다

    /**
     * 섹션 칸 원시 쓰기(cellIndex = y*256+z*16+x, 섹션 안).
     * vanillaCounts 면 바닐라 LevelChunkSection.setBlockState 로 쓰고(개수 셋 ±1), 아니면 살아 있는 블록 컨테이너 PalettedContainer.set/getAndSet 으로 쓴다(개수 그대로).
     *
     * <p>하이트맵, 빛, 하늘빛 원천, 저장 표시, 클라이언트, 블록 엔티티, POI, 이웃은 건드리지 않는다.
     * 하이트맵은 처음 쓸 때 고정 하이트맵 모드로 들어가 낡은 값을 든다.
     * 옛 상태 번호를 돌려주고, 로드돼 있지 않으면 -1. 범위 밖 번호는 IllegalArgumentException.
     */
    int setSectionStateRawNow(World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int stateId, boolean vanillaCounts);

    /** 섹션 바이옴 칸 원시 쓰기(cellIndex = y*16+z*4+x, 4×4×4). 바닐라 바이옴 컨테이너 set/getAndSet 이다. 옛 바이옴 번호를 돌려주고, 로드돼 있지 않으면 -1. */
    int setSectionBiomeRawNow(World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int biomeId);

    /**
     * 섹션의 바닐라 개수 {nonEmptyBlockCount, tickingBlockCount, tickingFluidCount}. 바닐라 그대로다.
     * 읽은 섹션은 유체 칸을 nonEmpty 에 두 번 센다. 칸 쓰기는 하나씩 ±1 하고, 컨테이너 원시 쓰기는 그대로 두며, 대입하면 그 값이 된다. 로드돼 있지 않으면 null.
     */
    int[] sectionCountsNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션의 바닐라 개수를 그 값으로(short 범위, 플러그인이 필드에 대입한 값). 무작위 틱 대상이 따라간다. 로드돼 있지 않으면 false. */
    boolean setSectionCountsNow(World world, int chunkX, int sectionIndex, int chunkZ, int nonEmptyBlockCount, int tickingBlockCount, int tickingFluidCount);

    /**
     * 바닐라 Heightmap.setRawData 와 같다. 길이가 바닐라 배열과 같으면 그 값을 그대로 쓰고(1), 다르면 경고를 남기고 그 종류만 지금 블록에서 prime 한다(0).
     * 고정 하이트맵 모드로 들어간다. 저장 표시는 하지 않는다. heightmap 은 바닐라 직렬화 키(살아 있는 네 종류)다. 로드돼 있지 않으면 -1.
     */
    int setHeightmapRawNow(World world, int chunkX, int chunkZ, String heightmap, long[] raw);

    /** 바닐라 Heightmap.update(x, y, z, state) 그대로다(주어진 상태로 술어를 보고, 내려갈 때 칸을 읽는다). 바뀌면 1, 아니면 0, 로드돼 있지 않으면 -1. 고정 모드로 들어간다. */
    int updateHeightmapNow(World world, String heightmap, int x, int y, int z, int stateId);

    // ------------------------------------------------------------------ 다른 스레드 읽기(N2b-3): 기다리지 않는 공개 스냅샷

    /**
     * 바닐라 LevelChunk.getBlockState 의 번호판이다. 섹션이 바닐라 hasOnlyAir(nonEmptyBlockCount == 0)면 공기(0), 아니면 그 칸의 값이다.
     * 높이 밖은 부르는 쪽이 거른다. 로드돼 있지 않으면 -1. ({@link #stateIdNow} 와 {@link #sectionCountsNow} 를 배열 없이 한 번에 하는 것이다.)
     */
    int vanillaStateIdNow(World world, int x, int y, int z);

    /**
     * 블록 엔티티가 있는 칸이다(바닐라 ChunkAccess.getBlockEntitiesPos. 블록 엔티티와 아직 만들지 않은 생성 블록 엔티티를 포함한다).
     * 월드 좌표를 바닐라 BlockPos.asLong 꼴로 돌려준다. 로드돼 있지 않으면 null.
     */
    long[] blockEntityPositionsNow(World world, int chunkX, int chunkZ);

    /**
     * 그 청크의 공개 스냅샷(아무 스레드, 기다리지 않음). 주인 region 이 틱 끝마다 새로 내며, 바뀐 것이 있을 때만 낸다.
     * 아직 없으면 null 을 주고 주인에게 다음 틱 끝에 공개해 달라고 부탁한다. 로드돼 있지 않아도 null 이다.
     * 다른 스레드가 이 메서드로 읽으면 그 청크의 공개가 이어진다(한동안 읽지 않으면 멈춘다).
     */
    ChunkView chunkView(World world, int chunkX, int chunkZ);

    /**
     * {@link #chunkView} 의 기다리는 버전이다(바닐라 ServerChunkCache.getChunk 가 다른 스레드에서 주 스레드를 기다리는 것과 같은 간선).
     * 주인 스레드면 곧바로 공개해 준다. region·전역이 아닌 스레드면 주인이 공개할 때까지 기다린다(상한 있음).
     * 다른 region 의 스레드와 전역 스레드에서는 교착을 피하려고 기다리지 않는다(규칙 6). 그때 공개본이 없으면 IllegalStateException 을 던진다.
     * 로드돼 있지 않으면 null.
     */
    ChunkView awaitChunkView(World world, int chunkX, int chunkZ);

    /** 이번 로드에 붙은 호환 계층 청크 객체(LevelChunk shim). 없거나 사라졌으면(약한 참조) null. 아무 스레드. */
    Object chunkShim(World world, int chunkX, int chunkZ);

    /**
     * shim 을 이번 로드에 붙인다. 이미 살아 있는 shim 이 붙어 있으면 그것을 돌려준다(먼저 붙은 쪽이 이기고, 어느 스레드에서나 같은 객체다).
     * 서버는 shim 을 약하게만 쥔다. 붙은 shim 이 살아 있는 동안 주인이 공개 스냅샷을 이어 낸다(shim 이 다른 스레드로 넘어가도 읽히게 하려는 것이다, 설계 R16).
     * 주인 스레드에서 붙이면 그 자리에서 공개한다. 로드돼 있지 않으면 null.
     */
    Object attachChunkShim(World world, int chunkX, int chunkZ, Object shim);

    // ------------------------------------------------------------------ 거울·들여오기(N2b-4): 플러그인이 고친 거울을 주인이 서버 청크로 맞춘다

    /**
     * {@link #attachChunkShim(World, int, int, Object)} 에 거울을 함께 붙인다.
     * 서버는 shim 과 거울을 약하게 쥔다(둘은 서로를 강하게 쥐어 함께 사라진다).
     * 들여오지 않은 쓰기가 있는 동안({@link #requestMirrorSync})만 거울을 강하게 쥔다.
     * 주인 region 은 그 청크의 맞춤 지점(틱 끝 훑기, 주인 스레드 읽기, 저장, 언로드)마다 {@link ChunkMirror#sync} 를 부른다.
     */
    Object attachChunkShim(World world, int chunkX, int chunkZ, Object shim, ChunkMirror mirror);

    /**
     * 아무 스레드. 이 로드에 붙은 거울에 들여오지 않은 쓰기가 생겼다고 알린다.
     * 거울을 강하게 쥐고 주인에게 맞추기를 부탁한다(다음 맞춤 지점, 늦어도 그 틱 끝).
     * 같은 청크의 부탁은 주인이 맞출 때까지 한 번만 간다.
     * 이 로드에 붙은 거울이 아니면(내린 청크나 옛 로드의 거울) false 이고, 그 쓰기는 월드에 닿지 않는다.
     */
    boolean requestMirrorSync(World world, int chunkX, int chunkZ, ChunkMirror mirror);

    /**
     * 주인 스레드. 섹션을 통째로 들여온다(바닐라에 없는 단일 입구, 설계 3.4).
     * states(4096, YZX 상태 번호), biomes(64, 바이옴 번호), counts(바닐라 개수 셋) 중 null 인 것은 그대로 둔다.
     * 블록만 바꾸면 바닐라처럼 개수는 그대로다(바닐라는 컨테이너 데이터를 갈아도 다시 세지 않는다).
     *
     * <p>저장 표시, 빛, 하이트맵 재계산, 블록 엔티티, POI, 패킷, 이웃은 건드리지 않는다.
     * 블록을 바꾸면 하이트맵은 고정 모드로 들어가 낡은 값을 든다.
     * 로드돼 있지 않으면 false. 범위 밖 번호는 IllegalArgumentException.
     */
    boolean importSectionNow(World world, int chunkX, int sectionIndex, int chunkZ, int[] states, int[] biomes, int[] counts);

    /** 주인 스레드. 그 청크 내용의 버전 번호다(블록·바이옴·빛이 바뀌면 오르며, {@link ChunkView#contentStamp} 와 같은 수). 로드돼 있지 않으면 -1. */
    long contentStampNow(World world, int chunkX, int chunkZ);

    /**
     * 주인 스레드. 그 청크의 공개 스냅샷을 지금 낸다(바뀐 것이 없으면 앞 스냅샷). 로드돼 있지 않으면 null.
     * 거울이 들여온 섹션을 보기로 붙이기 직전에 부른다. 붙은 보기를 읽는 다른 스레드가 들여온 내용을 보게 하려는 것이다.
     */
    ChunkView publishChunkViewNow(World world, int chunkX, int chunkZ);

    // ------------------------------------------------------------------ 블록 엔티티(T-198) — 서버 블록 엔티티 객체를 불투명 손잡이(Object)로 주고받는다

    /**
     * 주인 스레드: 그 칸의 블록 엔티티 손잡이(바닐라 LevelChunk.getBlockEntity(pos, 종류)). create 면 IMMEDIATE — 없고 블록이 블록 엔티티를 가지는 종류면
     * 블록이 새로 만들어 청크에 둔다(틱·게임 이벤트 등록 포함, 저장 표시 없음). 아직 해석하지 않은 생성 NBT 만 있는 칸은 그 NBT 의 손잡이를 준다.
     * 없으면(또는 로드돼 있지 않으면) null.
     */
    Object blockEntityNow(World world, int x, int y, int z, boolean create);

    /**
     * 주인 스레드: 바닐라 LevelChunk.removeBlockEntity — 그 칸의 블록 엔티티(생성 NBT 포함)를 청크에서 떼고 지움 표시, 틱·게임 이벤트에서 뺀다. 블록은 그대로,
     * 저장 표시 없음. 뗀 손잡이(없으면 null).
     */
    Object removeBlockEntityNow(World world, int x, int y, int z);

    /** 손잡이의 블록 엔티티가 지워졌는가(청크에서 떨어졌거나 갈렸다). 아무 스레드(경쟁 읽기 — Paper 의 isRemoved 와 같은 성질). */
    boolean blockEntityRemoved(Object blockEntity);

    /** 손잡이의 저장 꼴(바닐라 saveWithFullMetadata — id·x·y·z 포함, 새 트리). 살아 있는 것은 그 칸의 주인 스레드에서, 떨어진 것은 아무 스레드. */
    org.domi.furnace.api.nbt.NbtCompound blockEntitySaveNow(Object blockEntity);

    /**
     * 주인 스레드: 바닐라 BlockEntity.loadWithComponents(tag) — 종류별 필드·영속 데이터·컴포넌트를 싣는다. 저장 표시·패킷·이웃 알림은 없다(바닐라 그대로 —
     * 부르는 쪽이 setChanged 등을 고른다). 지워진(떨어진) 블록 엔티티에도 싣는다(그 객체에만 남는다). 싣지 못하면 false.
     */
    boolean blockEntityLoadNow(World world, Object blockEntity, org.domi.furnace.api.nbt.NbtCompound tag);

    /**
     * 주인 스레드: 바닐라 정적 BlockEntity.setChanged(level, pos, state) — 그 청크 저장 표시(로드돼 있을 때), 그 칸 블록이 공기가 아니면 비교기 쪽 알림
     * (updateNeighbourForOutputSignal). 로드돼 있지 않으면 false.
     */
    boolean blockEntityChangedNow(World world, int x, int y, int z);

    /**
     * 틱 스레드가 아닌 스레드가 결과를 막고 기다릴 짧은 작업을 그 칸의 주인 region 에 보낸다(블록 엔티티 저장 왕복). 결과·실행 스레드는
     * {@code world.scheduler(pos).submit(task)} 와 같고, 주인이 틱 사이에 쉬고 있으면 다음 틱 머리를 기다리지 않고 곧바로 돈다(명령 큐 문과 같은
     * drain 차례·예산 — 예산을 넘으면 예전처럼 다음 틱 머리). 작업 하나가 한 틱(50ms)씩 걸려 FAWE 가 상자 512개를 12초에 지우던 것(T-198 실측)을 푼다.
     */
    default <T> java.util.concurrent.CompletableFuture<T> submitAwaited(World world, int x, int y, int z, java.util.function.Supplier<T> task) {
        return world.scheduler(new org.domi.furnace.api.world.BlockPos(x, y, z)).submit(task);
    }

    /**
     * 아무 스레드: 레벨에 놓이지 않은 블록 엔티티에 tag 를 실었다가 다시 저장한 꼴(바닐라 loadWithComponents → saveWithoutMetadata, id 포함). 그 상태가
     * 블록 엔티티를 만들지 않으면 null.
     */
    org.domi.furnace.api.nbt.NbtCompound normalizeBlockEntityNbt(int stateId, org.domi.furnace.api.nbt.NbtCompound tag);

    // ------------------------------------------------------------------ 빛 다시 계산(T-197)

    /** 청크 좌표 콜백. */
    @FunctionalInterface
    interface ChunkCallback {
        void accept(int chunkX, int chunkZ);
    }

    /**
     * 아무 스레드, 기다리지 않음. Paper(Starlight) {@code ThreadedLevelLightEngine#starlight$serverRelightChunks} 의 서버 쪽 구현이다.
     *
     * <p>chunkX[i]·chunkZ[i] 중 지금 로드된 청크만 받는다(순서 유지, 같은 청크는 한 번).
     * 받은 청크는 빛을 지금 블록으로 처음부터 다시 계산해 그 청크에만 끼운다. 둘레 청크의 빛은 그대로다(Paper 와 같다).
     * 끼운 청크는 저장 대상이 되고, 그 청크를 보는 모든 플레이어에게 전 섹션 빛 갱신 패킷이 간다.
     *
     * <p>청크마다 끝나면 chunkDone 을, 모두 끝나면 allDone(받은 수)을 부른다.
     * 요청마다 콜백 전용 스레드 한 줄에서 끝난 차례대로 부른다(Paper 는 빛 일꾼 한 스레드다).
     * 돌려주는 값은 받은 수다. 받은 청크는 끝날 때까지 내려가지 않는다. 콜백은 null 이어도 된다.
     */
    int relightChunks(World world, int[] chunkX, int[] chunkZ, ChunkCallback chunkDone, java.util.function.IntConsumer allDone);

    // ------------------------------------------------------------------ 빛 읽기·검사(T-205) — Paper 빛 엔진 표면(층 듣기·checkBlock·getRawBrightness)의 서버 몫
    // 빛 섹션 번호는 바닐라 SectionPos.y(절대값)다. 빛 섹션은 블록 섹션 아래위로 하나씩 더 있다(바닐라 getMinLightSection = 최저 섹션 − 1).

    /** 그 차원에 하늘빛이 있는가(바닐라 DimensionType.hasSkyLight). 아무 스레드. */
    boolean hasSkyLight(World world);

    /** 주인 스레드. 그 청크가 로드돼 있고 빛이 켜져 있는가(바닐라·Paper isLightCorrect 이고 LIGHT 단계 이상). 로드돼 있지 않으면 false. */
    boolean lightReadyNow(World world, int chunkX, int chunkZ);

    /**
     * 주인 스레드. 그 칸의 저장된 빛 값이다(sky 면 하늘빛, 아니면 블록빛, 0~15).
     * 로드돼 있지 않거나, 빛 자료가 없거나, 서버 빛 배열 높이 밖이면 -1 이다.
     * 빛 섹션 범위 밖 규칙은 부르는 쪽이 정한다(서버 빛 배열은 오버월드 빛 섹션 범위만 가진다).
     */
    int lightNow(World world, int x, int y, int z, boolean sky);

    /** 주인 스레드. 빛 섹션 하나의 니블 배열 사본이다(2048 바이트, 바닐라 DataLayer 순서 YZX). 로드돼 있지 않거나 빛 자료가 없거나 빛 섹션 범위 밖이면 null. */
    byte[] lightLayerNow(World world, int chunkX, int sectionY, int chunkZ, boolean sky);

    /**
     * 아무 스레드, 기다리지 않음. 그 칸의 빛 검사를 건다(Paper {@code LevelLightEngine#checkBlock}).
     * 그 칸의 블록이 바뀌었을 수 있으니 하늘빛 원천과 두 빛을 지금 블록으로 다시 본다.
     *
     * <p>계산은 그 청크 주인 region 의 다음 빛 계산(틱 끝)에서 하고, 바뀐 빛은 저장 대상이 되며 보는 플레이어에게 간다.
     * 다른 스레드에서 부르면 주인에게 보내고(주인이 그 청크의 거울 쓰기를 먼저 들여온다), 빛 다시 계산(relight)이 그 둘레를 계산하는 동안에는 끼운 뒤로 미룬다.
     * 높이 밖이거나 로드되지 않았거나 빛이 켜지지 않은 청크는 버린다(Paper 와 같다).
     * 받았으면 true 다(다른 스레드에서는 주인에게 보냈으면 true).
     */
    boolean checkLight(World world, int x, int y, int z);

    // ------------------------------------------------------------------ 레벨 공통(T-211 3단계)

    /** 아무 스레드. 이 서버에 켜진 기능 플래그 이름(바닐라 등록 순서, 예: "minecraft:vanilla") — 바닐라 {@code Level#enabledFeatures}. */
    default List<String> enabledFeatureFlags() { throw new UnsupportedOperationException("enabledFeatureFlags"); }

    /**
     * 바닐라 {@code Level#getCurrentDifficultyAt}: {난이도 번호, 실효 난이도}. 청크가 로드돼 있으면 그 칸 주인 스레드(빌림 포함)에서만 — 아니면
     * ThreadOwnershipException — 그 청크의 머문 시간·달 밝기를 넣고, 로드돼 있지 않으면 아무 스레드에서 둘 다 0 으로 계산한다.
     */
    default float[] currentDifficultyNow(World world, int x, int y, int z) { throw new UnsupportedOperationException("currentDifficultyNow"); }

    /**
     * 주인 스레드. 바닐라 {@code Level#destroyBlock(pos, drop, breaker, maxUpdateDepth)} — 부순 블록이 있었고 바꿨으면 true.
     * breaker 는 null 이거나 이 스레드가 가진 엔티티(아니면 IllegalArgumentException). 청크가 로드돼 있지 않으면 false.
     */
    default boolean destroyBlockNow(World world, int x, int y, int z, boolean drop, org.domi.furnace.api.entity.Entity breaker, int maxUpdateDepth) {
        throw new UnsupportedOperationException("destroyBlockNow");
    }

    /**
     * 주인 스레드. 바닐라 {@code Clearable#clearContent}: {@link #blockEntityNow} 가 준 서버 블록 엔티티의 내용을 바닐라처럼 비운다
     * (주크박스는 재생을 멈추고, 장식 단지는 전리품을 먼저 푼다). 비울 수 있는 종류가 아니거나 서버가 모르는 블록 엔티티면 false.
     */
    default boolean clearBlockEntityContentNow(World world, Object blockEntity) { throw new UnsupportedOperationException("clearBlockEntityContentNow"); }

    // ------------------------------------------------------------------ NMS 엔티티(T-212: FAWE 엔티티 복사·지우기)

    /**
     * 그 엔티티를 가진 스레드(전역 빌림 포함). 바닐라 {@code Entity#save}: 승객(탄 것은 탈것이 저장)·저장하지 않는 종류(플레이어)·저장하지 않는 지움 이유면
     * null, 아니면 "id" 가 든 저장 NBT(승객까지). 엔티티가 이미 사라졌으면 null(Paper 에서 지워진 엔티티의 save 는 거짓). 주인이 아니면 ThreadOwnershipException.
     */
    default org.domi.furnace.api.nbt.NbtCompound entitySaveNow(org.domi.furnace.api.entity.Entity entity) {
        throw new UnsupportedOperationException("entitySaveNow");
    }

    /**
     * 그 엔티티를 가진 스레드(전역 빌림 포함). 바닐라 {@code Entity#discard} — 지움 이유 DISCARDED, 이벤트 없음(Paper 도 원인이 없으면 EntityRemoveEvent 를 내지 않는다).
     * 이미 사라졌으면 아무 일도 하지 않는다. 플레이어는 IllegalArgumentException. 주인이 아니면 ThreadOwnershipException.
     */
    default void entityDiscardNow(org.domi.furnace.api.entity.Entity entity) { throw new UnsupportedOperationException("entityDiscardNow"); }

    /**
     * 칸 (x, z) 의 주인 스레드(전역 빌림 포함). NMS 로 만든 엔티티(바닐라 {@code EntityType#create} 뒤 아직 월드에 없는 것)를 서버 엔티티로 만들어
     * ops 를 차례로 재생(바닐라 {@code Entity#load}·{@code absMoveTo}·{@code setUUID})하고, spawnReason(Bukkit 이름, null 이면 서버 기본)을 붙여 월드에 넣는다.
     * fireEvents 면 Paper {@code ServerLevel#addFreshEntity(entity, reason)} — 스폰 이벤트를 내고, 빈 아이템 엔티티는 넣지 않는다. 아니면 Paper
     * {@code EntityLookup#addNewEntity} — 이벤트 없이 넣는다. (x, z) 는 재생 뒤 자리여야 한다(아니면 ThreadOwnershipException).
     * 넣었으면 {@link NmsEntityAdd#added} 가 그 핸들, 넣지 못했으면(UUID 겹침·이벤트 취소·처리기가 지움·청크가 로드되지 않음) null 이고,
     * {@link NmsEntityAdd#discarded} 는 Paper 가 그 엔티티를 지우는 경우(스폰 이벤트 취소·처리기가 지움 — {@code CraftEventFactory#doEntityAddEventCalling})만 참.
     * id 를 서버가 만들 수 없으면 IllegalArgumentException.
     */
    default NmsEntityAdd addNmsEntityNow(World world, int x, int z, String id, java.util.List<EntityOp> ops, String spawnReason, boolean fireEvents) {
        throw new UnsupportedOperationException("addNmsEntityNow");
    }

    /** {@link #addNmsEntityNow} 의 결과: 넣은 엔티티(못 넣었으면 null)와, 못 넣은 엔티티를 Paper 처럼 지운 것으로 볼지. */
    record NmsEntityAdd(org.domi.furnace.api.entity.Entity added, boolean discarded) {
        public static final NmsEntityAdd REFUSED = new NmsEntityAdd(null, false);
    }

    /** {@link #addNmsEntityNow} 가 재생할 엔티티 조작 하나. */
    sealed interface EntityOp {
        /** 바닐라 {@code Entity#load(tag)}. */
        record Load(org.domi.furnace.api.nbt.NbtCompound tag) implements EntityOp {}

        /** 바닐라 {@code Entity#absMoveTo(x, y, z, yRot, xRot)}. */
        record AbsMoveTo(double x, double y, double z, float yRot, float xRot) implements EntityOp {}

        /** 바닐라 {@code Entity#setUUID(uuid)}. */
        record SetUuid(java.util.UUID uuid) implements EntityOp {}
    }

    /**
     * 청크 shim 의 거울이다(호환 계층이 만들며, 플러그인이 볼 수 없는 내부 객체). 서버는 그 청크의 주인 region 스레드에서만 부르고, 이 객체의 모니터를 쓰지 않는다.
     */
    interface ChunkMirror {
        /** 맞추기. 거울의 바뀐 부분(칸 일지, 교체된 섹션, 필드 대입)을 서버 청크로 들여온다. 맞추는 동안 들어온 쓰기가 남았으면 true. */
        boolean sync();

        /** 틱 끝 공개 뒤에 부른다(view 는 방금 낸 스냅샷). 붙은 섹션의 개수 필드와 자기 쓰기 덧칠을 새 스냅샷에 맞춘다. */
        void published(ChunkView view);

        /** 청크가 내려가기 직전(맞춘 뒤, 아직 로드돼 있다)에 부른다. 섹션을 떼어 내 그 뒤 쓰기가 월드에 닿지 않게 한다(바닐라에서는 떨어진 LevelChunk 에 써질 뿐이다). */
        void unloaded();
    }

    /**
     * 공개 스냅샷이다. 주인 region 이 틱 끝에 낸 그 청크의 불변 사본이며, 기다리지 않고 아무 스레드에서 읽는다.
     * 값은 최대 한 틱 묵은 것일 수 있다(Paper 의 경쟁 읽기와 같은 성질).
     * 한 스냅샷 안의 값은 서로 맞는다(같은 순간). 섹션 번호 0 이 맨 아래이고, 칸 좌표는 월드 좌표(높이 안)다.
     */
    interface ChunkView {
        /** 스냅샷 버전 번호. 그 청크 로드 동안 1 부터 오르고, 바뀐 것이 없으면 같은 스냅샷이 이어진다. */
        long version();

        /** 이 스냅샷을 낸 로드의 표시({@link #loadedChunkToken} 과 같은 객체). */
        Object loadToken();

        /** 이 스냅샷을 낸 순간의 내용 버전 번호({@link #contentStampNow} 와 같은 수). */
        long contentStamp();

        int sectionCount();

        /** 칸의 상태 번호. */
        int stateId(int x, int y, int z);

        /** {@link #vanillaStateIdNow} 와 같은 판정이다(섹션 바닐라 nonEmpty == 0 이면 공기). */
        int vanillaStateId(int x, int y, int z);

        /** 섹션 칸 4096개(YZX, 새 배열). */
        int[] sectionStates(int sectionIndex);

        /** T-209: 섹션 블록 저장소({@link PackedStates}, 서버 배치 그대로). 펼친 배열만 가진 섹션이면 null — 그때는 {@link #sectionStates}. */
        default PackedStates sectionStatesPacked(int sectionIndex) { return null; }

        /** Furnace 의 정확한 비공기 칸 수({@link #sectionNonAirNow} 와 같은 뜻). */
        int sectionNonAir(int sectionIndex);

        /** 바닐라 개수 셋(새 배열, {@link #sectionCountsNow} 와 같은 뜻). */
        int[] sectionCounts(int sectionIndex);

        /** {@link #sectionRandomTickingPartsNow} 와 같은 비트. */
        int sectionRandomTickingParts(int sectionIndex);

        /** 바이옴 번호 64개(새 배열). */
        int[] sectionBiomes(int sectionIndex);

        /** {@link #firstAvailableNow} 와 같은 값(청크 안 x·z 0~15). 모르는 키면 IllegalArgumentException. */
        int firstAvailable(String heightmap, int localX, int localZ);

        /** {@link #blockEntityPositionsNow} 와 같은 값(새 배열). */
        long[] blockEntityPositions();

        /** {@link #lightReadyNow} 와 같은 판정이다(이 스냅샷을 낸 순간). */
        boolean lightReady();

        /** {@link #lightNow} 와 같은 값이다(이 스냅샷을 낸 순간). 빛 자료가 없으면 -1. */
        int light(boolean sky, int x, int y, int z);

        /** {@link #lightLayerNow} 와 같은 값이다(새 배열, 이 스냅샷을 낸 순간). */
        byte[] lightLayer(boolean sky, int sectionY);
    }
}
