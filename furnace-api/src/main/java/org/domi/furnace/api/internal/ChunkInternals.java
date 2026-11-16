// 청크 내부 읽기 다리(N2a) — Bukkit·Paper 호환 계층의 NMS 청크 shim(LevelChunk·LevelChunkSection·PalettedContainer·Heightmap)이
// 서버 청크를 "보기"로 읽는 길. 블록 상태 번호(BlockTypes.stateId 와 같은 번호)·바이옴 레지스트리 번호 단위로 다룬다.
//
// 불안정 내부 다리다(Internals 와 같은 등급, 호환 계층 전용) — 일반 플러그인은 World·Block API 를 쓴다. 쓰기는 단일 칸 두 개
// (바닐라 Level.setBlock(플래그·깊이 그대로)과 LevelChunk.setBlockState)와, N2b-2 의 주인 스레드 원시 쓰기(섹션 칸·바이옴 칸·바닐라 개수·하이트맵)다.
package org.domi.furnace.api.internal;

import java.util.List;
import org.domi.furnace.api.world.World;

/**
 * 청크 섹션·하이트맵·바이옴의 번호 단위 읽기. "Now" 가 붙은 것은 그 청크를 가진 region 스레드(전역 빌림 구간 포함)에서만 되고,
 * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}. 청크가 로드돼 있지 않으면 표시값(null·-1·{@link Integer#MIN_VALUE})을
 * 돌려줄 뿐 청크를 만들거나 불러오지 않는다. 섹션 번호는 0 = 맨 아래 섹션(바닐라 getSectionIndex).
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

    /** 그 칸의 블록 상태 번호. 높이 밖이면 공기(0)가 아니라 그 값 그대로 요청하지 말 것 — 높이 밖은 부르는 쪽이 거른다. 로드돼 있지 않으면 -1. */
    int stateIdNow(World world, int x, int y, int z);

    /** 섹션의 블록 상태 번호 4096개(YZX, 새 배열). 로드돼 있지 않으면 null. */
    int[] sectionStatesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 섹션의 공기 아닌 칸 수(air·cave_air·void_air 는 공기). 0 인지(바닐라 hasOnlyAir)는 바닐라와 같지만 값은 바닐라 nonEmptyBlockCount 와
     * 다를 수 있다(바닐라는 유체 있는 칸을 한 번 더 센다). 로드돼 있지 않으면 -1.
     */
    int sectionNonAirNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션에 무작위 틱 받는 칸(블록 또는 그 유체)이 있는가(바닐라 isRandomlyTicking). 로드돼 있지 않으면 false. */
    boolean sectionRandomlyTickingNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 섹션의 바닐라 무작위 틱 판정을 나눠서: 비트 1 = isRandomlyTickingBlocks(tickingBlockCount > 0), 비트 2 = isRandomlyTickingFluids
     * (tickingFluidCount > 0 — 칸 쓰기로 놓은 물도 센다, 바닐라 그대로). 로드돼 있지 않으면 0.
     */
    int sectionRandomTickingPartsNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션의 바이옴 레지스트리 번호 64개(4×4×4, YZX). 로드돼 있지 않으면 null. */
    int[] sectionBiomesNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /**
     * 그 블록 상태가 무작위 틱을 받는가(바닐라 BlockState.isRandomlyTicking — 블록 자체만, 유체 쪽은 제외). 모르는 번호면 false. 아무 스레드(고정 표).
     */
    boolean stateRandomlyTicking(int stateId);

    /**
     * 로드된 청크의 "이번 로드"를 나타내는 불투명 객체 — 같은 로드 동안 같은 객체, 내렸다 다시 올리면 다른 객체. 로드돼 있지 않으면 null. 아무 스레드.
     * 호환 계층이 청크 shim 의 정체성을 바닐라처럼 로드 동안 하나로 묶는 데 쓴다(N2b). 청크 객체가 아닌 전용 표시라 쥐어도 청크를 붙잡지 않는다.
     * 청크 재생성은 표시를 이어받는다(Paper 는 재생성해도 같은 LevelChunk).
     */
    Object loadedChunkToken(World world, int chunkX, int chunkZ);

    /** 바이옴 레지스트리의 키 목록(번호 순, 데이터 팩 바이옴 포함). 아무 스레드. */
    List<String> biomeKeys();

    /**
     * 하이트맵의 첫 빈 칸 y(바닐라 Heightmap.getFirstAvailable = 가장 높은 해당 블록 y + 1). heightmap 은 바닐라 직렬화 키
     * (WORLD_SURFACE·OCEAN_FLOOR·MOTION_BLOCKING·MOTION_BLOCKING_NO_LEAVES). 로드돼 있지 않으면 {@link Integer#MIN_VALUE},
     * 모르는 키면 IllegalArgumentException.
     */
    int firstAvailableNow(World world, String heightmap, int x, int z);

    /** 청크를 저장 대상으로 표시한다(바닐라 setUnsaved(true)). 로드돼 있지 않으면 false. */
    boolean markUnsavedNow(World world, int chunkX, int chunkZ);

    /**
     * 바닐라 Level.setBlock(pos, state, flags, maxUpdateDepth) 그대로 — 플래그 1·2·16·32·64 와 남은 깊이, 클라이언트 알림은 틱 끝에 모아 보내기,
     * 길찾기·POI 갱신까지. 바뀌었으면 true, 높이 밖·로드돼 있지 않음·같은 상태면 false.
     */
    boolean setBlockNow(World world, int x, int y, int z, int stateId, int flags, int maxUpdateDepth);

    /**
     * 바닐라 LevelChunk.setBlockState 그대로 — 그 칸만 바꾸고 놓기·치우기 반응·블록 엔티티·빛·하이트맵·저장 표시까지(이웃·모양 갱신, 클라이언트 알림,
     * POI 없음). 옛 상태 번호, 바뀌지 않았거나(같은 상태·공기뿐인 섹션에 공기) 치우기 반응이 칸을 갈아 치웠거나 높이 밖·로드돼 있지 않으면 -1.
     */
    int setBlockStateNow(World world, int x, int y, int z, int stateId, boolean moved);

    // ------------------------------------------------------------------ 원시 쓰기(N2b-2) — 바닐라처럼 그 값만 바꾸고 부수 효과가 없다

    /**
     * 섹션 칸 원시 쓰기(cellIndex = y*256+z*16+x, 섹션 안). vanillaCounts 면 바닐라 LevelChunkSection.setBlockState(개수 셋 ±1), 아니면 살아 있는
     * 블록 컨테이너 PalettedContainer.set/getAndSet(개수 그대로). 하이트맵(처음이면 고정 하이트맵 모드로 들어가 낡은 값을 든다)·빛·하늘빛 원천·
     * 저장 표시·클라이언트·블록 엔티티·POI·이웃은 건드리지 않는다. 옛 상태 번호, 로드돼 있지 않으면 -1. 범위 밖 번호는 IllegalArgumentException.
     */
    int setSectionStateRawNow(World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int stateId, boolean vanillaCounts);

    /** 섹션 바이옴 칸 원시 쓰기(cellIndex = y*16+z*4+x, 4×4×4) — 바닐라 바이옴 컨테이너 set/getAndSet. 옛 바이옴 번호, 로드돼 있지 않으면 -1. */
    int setSectionBiomeRawNow(World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int biomeId);

    /**
     * 섹션의 바닐라 개수 {nonEmptyBlockCount, tickingBlockCount, tickingFluidCount}(바닐라 그대로 — 읽은 섹션은 유체 칸을 nonEmpty 에 두 번 세고,
     * 칸 쓰기는 하나씩 ±1, 컨테이너 원시 쓰기는 그대로, 대입하면 그 값). 로드돼 있지 않으면 null.
     */
    int[] sectionCountsNow(World world, int chunkX, int sectionIndex, int chunkZ);

    /** 섹션의 바닐라 개수를 그 값으로(short 범위, 플러그인이 필드에 대입한 값). 무작위 틱 대상이 따라간다. 로드돼 있지 않으면 false. */
    boolean setSectionCountsNow(World world, int chunkX, int sectionIndex, int chunkZ, int nonEmptyBlockCount, int tickingBlockCount, int tickingFluidCount);

    /**
     * 바닐라 Heightmap.setRawData: 길이가 바닐라 배열과 같으면 그 값을 그대로(1), 다르면 경고를 남기고 그 종류만 지금 블록에서 prime(0). 고정 하이트맵 모드로
     * 들어간다. 저장 표시는 하지 않는다. heightmap 은 바닐라 직렬화 키(살아 있는 네 종류). 로드돼 있지 않으면 -1.
     */
    int setHeightmapRawNow(World world, int chunkX, int chunkZ, String heightmap, long[] raw);

    /** 바닐라 Heightmap.update(x, y, z, state) 그대로(주어진 상태로 술어, 내려갈 때 칸을 읽음). 바뀌면 1, 아니면 0, 로드돼 있지 않으면 -1. 고정 모드로 들어간다. */
    int updateHeightmapNow(World world, String heightmap, int x, int y, int z, int stateId);

    // ------------------------------------------------------------------ 다른 스레드 읽기(N2b-3) — 기다리지 않는 공개 스냅샷

    /**
     * 바닐라 LevelChunk.getBlockState 의 번호판: 섹션이 바닐라 hasOnlyAir(nonEmptyBlockCount == 0)면 공기(0), 아니면 그 칸. 높이 밖은 부르는 쪽이 거른다.
     * 로드돼 있지 않으면 -1. ({@link #stateIdNow} + {@link #sectionCountsNow} 를 배열 없이 한 번에.)
     */
    int vanillaStateIdNow(World world, int x, int y, int z);

    /**
     * 블록 엔티티가 있는 칸(바닐라 ChunkAccess.getBlockEntitiesPos — 블록 엔티티와 아직 만들지 않은 생성 블록 엔티티), 월드 좌표를 바닐라
     * BlockPos.asLong 꼴로. 로드돼 있지 않으면 null.
     */
    long[] blockEntityPositionsNow(World world, int chunkX, int chunkZ);

    /**
     * 그 청크의 공개 스냅샷(아무 스레드, 기다리지 않음). 주인 region 이 틱 끝마다(바뀐 것이 있을 때만) 새로 낸다. 없으면 null 을 주고 주인에게
     * 다음 틱 끝 공개를 부탁한다. 로드돼 있지 않으면 null. 다른 스레드가 이 판을 읽으면 그 청크의 공개가 이어진다(한동안 읽지 않으면 멈춘다).
     */
    ChunkView chunkView(World world, int chunkX, int chunkZ);

    /**
     * {@link #chunkView} 의 기다리는 판(바닐라 ServerChunkCache.getChunk 가 다른 스레드에서 주 스레드를 기다리는 것과 같은 간선): 주인 스레드면 곧바로 공개해
     * 주고, region·전역이 아닌 스레드면 주인이 공개할 때까지 기다린다(상한 있음). 다른 region 의 스레드와 전역 스레드에서는 기다리지 않는다(교착 — 규칙 6) — 그때 공개본이 없으면
     * IllegalStateException. 로드돼 있지 않으면 null.
     */
    ChunkView awaitChunkView(World world, int chunkX, int chunkZ);

    /** 이번 로드에 붙은 호환 계층 청크 객체(LevelChunk shim). 없거나 사라졌으면(약한 참조) null. 아무 스레드. */
    Object chunkShim(World world, int chunkX, int chunkZ);

    /**
     * shim 을 이번 로드에 붙인다 — 이미 살아 있는 것이 붙어 있으면 그것을 돌려준다(먼저 붙은 쪽이 이긴다, 아무 스레드에서 같은 객체). 서버는 shim 을
     * 약하게만 쥔다. 붙은 shim 이 살아 있는 동안 주인이 공개 스냅샷을 이어 낸다(그 shim 이 다른 스레드로 넘어가도 읽히도록, 설계 R16). 주인 스레드에서
     * 붙이면 그 자리에서 공개한다. 로드돼 있지 않으면 null.
     */
    Object attachChunkShim(World world, int chunkX, int chunkZ, Object shim);

    // ------------------------------------------------------------------ 거울·들여오기(N2b-4) — 플러그인이 고친 거울을 주인이 서버 청크로 맞춘다

    /**
     * {@link #attachChunkShim(World, int, int, Object)} 에 거울을 함께 붙인다. 서버는 shim 과 거울을 약하게 쥐고(둘은 서로를 강하게 쥐어 함께 사라진다),
     * 들여오지 않은 쓰기가 있는 동안({@link #requestMirrorSync})만 거울을 강하게 쥔다. 주인 region 은 그 청크의 맞춤 지점(틱 끝 훑기·주인 스레드 읽기·저장·언로드)
     * 마다 {@link ChunkMirror#sync} 를 부른다.
     */
    Object attachChunkShim(World world, int chunkX, int chunkZ, Object shim, ChunkMirror mirror);

    /**
     * 아무 스레드: 이 로드에 붙은 거울에 들여오지 않은 쓰기가 생겼다 — 거울을 강하게 쥐고 주인에게 맞추기를 부탁한다(다음 맞춤 지점, 늦어도 그 틱 끝).
     * 같은 청크의 부탁은 주인이 맞출 때까지 한 번만 간다. 이 로드에 붙은 거울이 아니면(내린 청크·옛 로드의 거울) false — 그 쓰기는 월드에 닿지 않는다.
     */
    boolean requestMirrorSync(World world, int chunkX, int chunkZ, ChunkMirror mirror);

    /**
     * 주인 스레드: 섹션 통째 들여오기(바닐라에 없는 단일 입구, 설계 3.4). states(4096, YZX 상태 번호)·biomes(64, 바이옴 번호)·counts(바닐라 개수 셋) 중
     * null 은 그대로 둔다 — 블록만 바꾸면 바닐라처럼 개수는 그대로(바닐라는 컨테이너 데이터를 갈아도 다시 세지 않는다). 저장 표시·빛·하이트맵 재계산
     * (블록을 바꾸면 고정 모드로 들어가 낡은 값을 든다)·블록 엔티티·POI·패킷·이웃은 건드리지 않는다. 로드돼 있지 않으면 false. 범위 밖 번호는 IllegalArgumentException.
     */
    boolean importSectionNow(World world, int chunkX, int sectionIndex, int chunkZ, int[] states, int[] biomes, int[] counts);

    /** 주인 스레드: 그 청크 내용의 판 번호(블록·바이옴·빛이 바뀌면 오른다, {@link ChunkView#contentStamp} 와 같은 수). 로드돼 있지 않으면 -1. */
    long contentStampNow(World world, int chunkX, int chunkZ);

    /**
     * 주인 스레드: 그 청크의 공개 스냅샷을 지금 낸다(바뀐 것이 없으면 앞 판). 로드돼 있지 않으면 null. 거울이 들여온 섹션을 보기로 붙이기 직전에 부른다 —
     * 붙은 보기를 읽는 다른 스레드가 들여온 내용을 보게.
     */
    ChunkView publishChunkViewNow(World world, int chunkX, int chunkZ);

    // ------------------------------------------------------------------ 빛 다시 계산(T-197)

    /** 청크 좌표 콜백. */
    @FunctionalInterface
    interface ChunkCallback {
        void accept(int chunkX, int chunkZ);
    }

    /**
     * 아무 스레드, 기다리지 않음: Paper(Starlight) {@code ThreadedLevelLightEngine#starlight$serverRelightChunks} 의 서버 몫. chunkX[i]·chunkZ[i] 중
     * 지금 로드된 청크만 받아(순서 유지, 같은 청크는 한 번) 빛을 지금 블록으로 처음부터 다시 계산하고 그 청크에만 끼운다(둘레 청크의 빛은 그대로 — Paper 와 같다).
     * 끼운 청크는 저장 대상이 되고, 그 청크를 보는 모든 플레이어에게 전 섹션 빛 갱신 패킷이 간다. 청크마다 끝나면 chunkDone, 모두 끝나면
     * allDone(받은 수) — 요청마다 콜백 전용 스레드 한 줄에서 끝난 차례대로 부른다(Paper 는 빛 일꾼 한 스레드). 돌려주는 값 = 받은 수. 받은 청크는 끝날 때까지 내려가지 않는다. 콜백은 null 이어도 된다.
     */
    int relightChunks(World world, int[] chunkX, int[] chunkZ, ChunkCallback chunkDone, java.util.function.IntConsumer allDone);

    // ------------------------------------------------------------------ 빛 읽기·검사(T-205) — Paper 빛 엔진 표면(층 듣기·checkBlock·getRawBrightness)의 서버 몫
    // 빛 섹션 번호는 바닐라 SectionPos.y(절대값)다. 빛 섹션은 블록 섹션 아래위로 하나씩 더 있다(바닐라 getMinLightSection = 최저 섹션 − 1).

    /** 그 차원에 하늘빛이 있는가(바닐라 DimensionType.hasSkyLight). 아무 스레드. */
    boolean hasSkyLight(World world);

    /** 주인 스레드: 그 청크가 로드돼 있고 빛이 켜져 있는가(바닐라·Paper isLightCorrect 이고 LIGHT 단계 이상). 로드돼 있지 않으면 false. */
    boolean lightReadyNow(World world, int chunkX, int chunkZ);

    /**
     * 주인 스레드: 그 칸의 저장된 빛 값(sky = 하늘빛, 아니면 블록빛, 0~15). 로드돼 있지 않거나 빛 자료가 없으면 -1. y 는 빛 섹션 범위 안
     * (최저 빛 섹션 ×16 ~ 최고 빛 섹션 ×16 + 15) — 밖은 IndexOutOfBoundsException(범위 밖 규칙은 부르는 쪽이 정한다).
     */
    int lightNow(World world, int x, int y, int z, boolean sky);

    /** 주인 스레드: 빛 섹션 하나의 니블 배열 사본(2048 바이트, 바닐라 DataLayer 순서 YZX). 로드돼 있지 않거나 빛 자료가 없거나 빛 섹션 범위 밖이면 null. */
    byte[] lightLayerNow(World world, int chunkX, int sectionY, int chunkZ, boolean sky);

    /**
     * 아무 스레드, 기다리지 않음: 그 칸의 빛 검사를 건다(Paper {@code LevelLightEngine#checkBlock} — 그 칸의 블록이 바뀌었을 수 있으니 하늘빛 원천과
     * 두 빛을 지금 블록으로 다시 본다). 계산은 그 청크 주인 region 의 다음 빛 계산(틱 끝)에서 하고, 바뀐 빛은 저장 대상이 되며 보는 플레이어에게 간다.
     * 다른 스레드면 주인에게 보내고(주인이 그 청크의 거울 쓰기를 먼저 들여온다), 빛 다시 계산(relight)이 그 둘레를 계산하는 동안에는 끼운 뒤로 미룬다.
     * 높이 밖·로드되지 않았거나 빛이 켜지지 않은 청크는 버린다(Paper 와 같다). 받았으면 true(다른 스레드는 주인에게 보냈으면 true).
     */
    boolean checkLight(World world, int x, int y, int z);

    /**
     * 청크 shim 의 거울(호환 계층이 만든다, 플러그인이 볼 수 없는 내부 객체). 서버는 그 청크의 주인 region 스레드에서만 부르고, 이 객체의 모니터를 쓰지 않는다.
     */
    interface ChunkMirror {
        /** 맞추기: 거울의 바뀐 부분(칸 일지·교체된 섹션·필드 대입)을 서버 청크로 들여온다. 맞추는 동안 들어온 쓰기가 남았으면 true. */
        boolean sync();

        /** 틱 끝 공개 뒤(view = 방금 낸 판): 붙은 섹션의 개수 필드와 자기 쓰기 덧칠을 새 판에 맞춘다. */
        void published(ChunkView view);

        /** 청크가 내려가기 직전(맞춘 뒤, 아직 로드돼 있다): 섹션을 떼어 내 그 뒤 쓰기가 월드에 닿지 않게 한다(바닐라: 떨어진 LevelChunk 에 써질 뿐). */
        void unloaded();
    }

    /**
     * 공개 스냅샷 — 주인 region 이 틱 끝에 낸 그 청크의 불변 사본. 기다리지 않고 아무 스레드에서 읽는다(최대 한 틱 옛 값 — Paper 의 경쟁 읽기와 같은 성질).
     * 한 판 안의 값은 서로 맞는다(같은 순간). 섹션 번호 0 = 맨 아래, 칸 좌표는 월드 좌표(높이 안).
     */
    interface ChunkView {
        /** 판 번호 — 그 청크 로드 동안 1 부터 오르고, 바뀐 것이 없으면 같은 판이 이어진다. */
        long version();

        /** 이 판을 낸 로드의 표시({@link #loadedChunkToken} 과 같은 객체). */
        Object loadToken();

        /** 이 판을 낸 순간의 내용 판 번호({@link #contentStampNow} 와 같은 수). */
        long contentStamp();

        int sectionCount();

        /** 칸의 상태 번호. */
        int stateId(int x, int y, int z);

        /** {@link #vanillaStateIdNow} 와 같은 판정(섹션 바닐라 nonEmpty == 0 이면 공기). */
        int vanillaStateId(int x, int y, int z);

        /** 섹션 칸 4096개(YZX, 새 배열). */
        int[] sectionStates(int sectionIndex);

        /** Furnace 정확한 비공기 칸 수({@link #sectionNonAirNow} 와 같은 뜻). */
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

        /** {@link #lightReadyNow} 와 같은 판정(이 판을 낸 순간). */
        boolean lightReady();

        /** {@link #lightNow} 와 같은 값(이 판을 낸 순간). 빛 자료가 없으면 -1. */
        int light(boolean sky, int x, int y, int z);

        /** {@link #lightLayerNow} 와 같은 값(새 배열, 이 판을 낸 순간). */
        byte[] lightLayer(boolean sky, int sectionY);
    }
}
