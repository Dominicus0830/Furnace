// 청크 섹션: 블록 16³ + 바이옴 4³ palette 저장 + 비공기 수. 인덱스는 바닐라 YZX 순.
package org.domi.furnace.server.world;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.chunk.LevelChunkSection")
public final class ChunkSection {

    public static final int AIR = StateIds.AIR;
    /** 1.21.1 의 공기 상태(air·void_air·cave_air) — 바닐라 nonEmptyBlockCount 는 isAir 가 아닌 칸만 센다. */

    public static boolean isAir(int stateId) { return stateId == AIR || stateId == StateIds.VOID_AIR || stateId == StateIds.CAVE_AIR; }
    private static final int DEFAULT_DIRECT_BITS = 15;
    private static final int DEFAULT_BIOME_DIRECT_BITS = 7;

    private final PaletteStorage blocks;
    private final PaletteStorage biomes;
    private short nonAirCount = 0;
    private int[] scratch;
    /** 바닐라 개수 셋이 아직 정해지지 않았다(적재·통째 교체 뒤 — 처음 필요할 때 바닐라 recalcBlockCounts 로 센다). */
    private static final int UNKNOWN = Integer.MIN_VALUE;
    /**
     * 바닐라 tickingBlockCount(무작위 틱 받는 블록 상태 칸 — 용암 블록도 블록 쪽에서 센다). {@link #UNKNOWN} 이면 다시 세야 한다.
     * 칸 수가 아니라 바닐라 개수다: 블록 컨테이너 원시 쓰기(N2b-2)·개수 대입(FAWE clearCounts)은 칸과 어긋나게 하고, 대입으로 음수도 된다.
     * 무작위 틱 대상 판정(&gt; 0)과 T-053 마스크만 이 값을 본다 — 칸 고르기는 칸을 직접 읽는다(ChunkLevel.randomTickSections).
     */
    private int tickingCount = UNKNOWN;
    /**
     * 바닐라 nonEmptyBlockCount. 칸 쓰기는 공기 아닌 상태만 ±1, 재계수는 공기 아닌 칸 + 유체 있는 칸(유체 칸을 두 번 센다). 그래서 읽은 섹션에서
     * 물을 지우면 공기뿐이어도 0 이 아닐 수 있다(바닐라 hasOnlyAir 거짓). Furnace 정확한 비공기 수 {@link #nonAirCount} 와 따로 둔다 — 서버 내부는
     * 그 값을 쓰고, 이 값은 NMS 섹션 보기(hasOnlyAir·write)와 다리가 바닐라 값으로 내준다. tickingCount 가 UNKNOWN 이면 뜻이 없다.
     */
    private int nonEmpty;
    /**
     * 바닐라 tickingFluidCount. 칸 쓰기(setBlockState)는 비어 있지 않은 유체(물 포함)를 ±1 하지만, 재계수(recalcBlockCounts — 적재·생성 결과 통째 넣기)는
     * 무작위 틱 받는 유체(용암)만 센다. 그래서 양동이로 물을 놓은 구역은 대상이 되고 디스크에서 다시 읽은 구역은 아니다(바닐라 그대로). 칸 쓰기로 음수가 될 수 있다.
     * tickingCount 가 UNKNOWN(모름) 이면 뜻이 없다 — 다시 셀 때 함께 정한다.
     */
    private int fluidCount;
    /**
     * 이 구역을 배열에 가진 컬럼과 그 자리(T-053 무작위 틱 구역 마스크). tickingCount 가 0 ↔ 0 아님으로 바뀔 때 컬럼 마스크 비트를 고친다.
     * cowCopy 가 공유하는 쪽 컬럼은 주인이 아니다 — 그 컬럼은 공유 구역의 비트를 켜 둔다. 컬럼 밖(시험·빛 작업이 쥔 옛 객체)이면 null.
     */
    private ChunkColumn owner;
    private int ownerIndex;
    /**
     * 바뀜 표(N2b-3 공개 스냅샷): 블록·바이옴·바닐라 개수를 바꾸는 메서드마다 하나씩 오른다(지연 재계수는 결과가 블록에서 정해지므로 올리지 않는다).
     * 주인 스레드가 공개 스냅샷을 낼 때 같은 객체·같은 값이면 앞 판의 섹션 사본을 그대로 쓴다. sections() 로 받아 구역에 직접 쓰는 길도 여기서 잡힌다.
     */
    private int version;

    /** 바뀜 표(공개 스냅샷의 재사용 판정). 주인 스레드. */
    int version() { return version; }

    /** 상태별 무작위 틱 여부(블록 동작 쪽이 건다). 없으면 무작위 틱을 하지 않는다. */
    private static volatile boolean[] randomTicking;

    /** 상태별 "유체가 비어 있지 않다"(물·용암·물 머금은 블록·켈프 …) 표와 "유체가 용암" 표. 없으면 유체 개수는 늘 0(표 없이 쓰는 시험). */
    private static volatile boolean[] fluidPresent, fluidLava;

    public static void randomTickingTable(boolean[] table) { randomTicking = table; }

    /** 바닐라 tickingFluidCount 쪽 표(쓰기: 유체가 비어 있지 않은 상태, 재계수: 용암 상태). randomTickingTable 과 같은 길이. */
    public static void fluidTables(boolean[] present, boolean[] lava) {
        fluidLava = lava;
        fluidPresent = present;
    }

    /** LevelChunkSection.isRandomlyTicking: tickingBlockCount > 0 또는 tickingFluidCount > 0. */
    public boolean isRandomlyTicking() {
        int known = tickingCount;
        if (known != UNKNOWN) return known > 0 || fluidCount > 0;   // 대부분 이미 센 값이다 — 표(volatile)를 읽을 필요 없다(X1)
        boolean[] t = randomTicking;
        if (t == null) return false;
        recount(t);
        return tickingCount > 0 || fluidCount > 0;
    }

    /** 바닐라 isRandomlyTickingBlocks(1) · isRandomlyTickingFluids(2) 비트(NMS 섹션 보기용). 모르면 먼저 다시 센다. */
    public int randomTickingParts() {
        if (tickingCount == UNKNOWN) {
            boolean[] t = randomTicking;
            if (t == null) return 0;
            recount(t);
        }
        return (tickingCount > 0 ? 1 : 0) | (fluidCount > 0 ? 2 : 0);
    }

    /** 지금 블록에서 무작위 틱 받는 칸 수를 센다(캐시를 건드리지 않음). 표가 없으면 0. */
    private int countTicking(boolean[] t) {
        if (t == null) return 0;
        int n = 0;
        if (scratch != null) {
            for (int v : scratch) if (v < t.length && t[v]) n++;
        } else if (blocks.maybeHas(v -> v < t.length && t[v])) {   // 팔레트에 무작위 틱 상태가 하나도 없으면 4096칸을 읽을 것 없이 0 (팔레트에 남은 옛 항목은 안전한 쪽으로 true)
            for (int i = 0; i < 4096; i++) { int v = blocks.get(i); if (v < t.length && t[v]) n++; }
        }
        return n;
    }

    /** 바닐라 recalcBlockCounts 의 유체 몫: 용암 상태 칸 수. 팔레트에 용암 상태가 없으면 4096칸을 읽지 않는다(풀 등 다른 무작위 틱 블록 때문에 읽지 않게). */
    private int countLava(boolean[] lava) {
        if (lava == null) return 0;
        int n = 0;
        if (scratch != null) {
            for (int v : scratch) if (v < lava.length && lava[v]) n++;
        } else if (blocks.maybeHas(v -> v < lava.length && lava[v])) {
            for (int i = 0; i < 4096; i++) { int v = blocks.get(i); if (v < lava.length && lava[v]) n++; }
        }
        return n;
    }

    /** 바닐라 recalcBlockCounts 의 nonEmpty 유체 몫: 유체가 비어 있지 않은 칸 수(물·용암·물 머금은 블록 …). 표가 없으면 0. */
    private int countFluidPresent(boolean[] fp) {
        if (fp == null) return 0;
        int n = 0;
        if (scratch != null) {
            for (int v : scratch) if (v < fp.length && fp[v]) n++;
        } else if (blocks.maybeHas(v -> v < fp.length && fp[v])) {
            for (int i = 0; i < 4096; i++) { int v = blocks.get(i); if (v < fp.length && fp[v]) n++; }
        }
        return n;
    }

    /** 바닐라 recalcBlockCounts: 개수 셋을 지금 블록에서 다시 정한다. 표가 없으면(t == null) 무작위 틱·유체 몫은 0. */
    private void recount(boolean[] t) {
        int nonAir = nonAirCount;
        if (scratch != null) {   // 열린 scratch 는 바깥이 직접 고쳐 nonAirCount 가 낡았을 수 있다
            nonAir = 0;
            for (int v : scratch) if (!isAir(v)) nonAir++;
        }
        nonEmpty = nonAir + countFluidPresent(fluidPresent);
        counts(countTicking(t), countLava(fluidLava));
    }

    /** "무작위 틱 대상일 수 있음": 모름이거나 블록 수 > 0 이거나 유체 수 > 0(바닐라 isRandomlyTicking, 음수는 아님). */
    private static boolean maybeTicking(int blockN, int fluidN) {
        return blockN == UNKNOWN || blockN > 0 || fluidN > 0;
    }

    /** 센 값 둘을 바꾸고, "대상일 수 있음"이 바뀌었으면 주인 컬럼의 마스크 비트를 고친다(T-053). */
    private void counts(int blockN, int fluidN) {
        boolean was = maybeTicking(tickingCount, fluidCount);
        tickingCount = blockN;
        fluidCount = fluidN;
        boolean now = maybeTicking(blockN, fluidN);
        if (was != now && owner != null) owner.sectionTickingChanged(ownerIndex, now);
    }

    /** 개수를 모름으로 — 바깥이 블록을 통째로 바꾼다. 다시 셀 때 셋을 새로 정한다. */
    private void countsUnknown() {
        counts(UNKNOWN, 0);
    }

    /** 바닐라 개수 셋이 정해져 있게 한다(모르면 지금 블록에서 recalcBlockCounts). */
    private void ensureCounts() {
        if (tickingCount == UNKNOWN) recount(randomTicking);
    }

    /** 바닐라 개수 {nonEmptyBlockCount, tickingBlockCount, tickingFluidCount}(N2b-2 — NMS 섹션 보기·다리). 모르면 먼저 센다. */
    public int[] vanillaCounts() {
        ensureCounts();
        return new int[] {nonEmpty, tickingCount, fluidCount};
    }

    /**
     * 청크 패킷 섹션 머리의 nonEmptyBlockCount(바닐라 LevelChunkSection.write 는 필드 값을 그대로 쓴다): 바닐라 개수를 고치지 않고 본다 —
     * 모르면 바닐라 recalcBlockCounts 의 nonEmpty(공기 아닌 칸 + 유체 있는 칸)를 센다. 단 바닐라 값이 실제 비공기 수보다 작으면(NMS 컨테이너
     * 원시 쓰기·개수 대입 뒤) 비공기 수를 쓴다 — 클라이언트는 받은 값 위에 ±1 하므로 그 아래에서 시작하면 블록이 남은 섹션이 0 이 될 수 있다 — 결정 entries/2026-11-14-n2b2-hasonlyair-gap.md
     * 가 A(그대로)인 동안 클라이언트가 서버와 다른 블록을 보지 않게. 섹션을 바꾸지 않으므로 아무 스레드에서 불러도 된다(주인이 쓰는 중이 아니면).
     */
    public int packetNonEmptyCount() {
        int[] s = scratch;
        int nonAir = nonAirCount;
        if (s != null) {
            nonAir = 0;
            for (int v : s) if (!isAir(v)) nonAir++;
        }
        int v = tickingCount != UNKNOWN ? nonEmpty : nonAir + countFluidPresent(fluidPresent);
        return Math.max(v, nonAir);   // 결정 A 동안: 클라이언트 개수(받은 값 ±1)가 서버 비공기 수 아래로 내려가 0 이 되지 않게(PR #382 검토 R2) — 원시 쓰기가 없으면 v >= nonAir 라 v 그대로
    }

    /** 바닐라 hasOnlyAir(nonEmptyBlockCount == 0). 모르면 먼저 센다. */
    public boolean vanillaHasOnlyAir() {
        ensureCounts();
        return nonEmpty == 0;
    }

    /**
     * 바닐라 개수 셋을 그 값으로(플러그인이 필드에 대입한 값 — FAWE clearCounts 의 0, 음수 포함 short 범위). 블록은 그대로다. 무작위 틱 대상·T-053 마스크가
     * 이 값을 따른다. 그 뒤 칸 쓰기는 이 값 위에 ±1 한다(바닐라 그대로).
     */
    public void setVanillaCounts(int nonEmptyBlockCount, int tickingBlockCount, int tickingFluidCount) {
        version++;
        nonEmpty = (short) nonEmptyBlockCount;
        counts((short) tickingBlockCount, (short) tickingFluidCount);
    }

    /** 이 구역을 컬럼 자리 i 에 놓는다 — 주인을 정하고 그 비트를 지금 값에 맞춘다. ChunkColumn 이 배열에 넣을 때만 부른다. */
    void attach(ChunkColumn col, int i) {
        owner = col;
        ownerIndex = i;
        col.sectionTickingChanged(i, maybeTicking(tickingCount, fluidCount));
    }

    /** 컬럼 배열에서 빠졌다(복사본으로 갈아 끼움) — 이 객체에 쓰기가 있어도 그 컬럼 마스크를 건드리지 않게. */
    void detach(ChunkColumn col) {
        if (owner == col) owner = null;
    }

    /**
     * 마스크 단언용(-ea, 틱마다): 비트를 끈 구역은 주인이 그 컬럼이고 센 값이 0 인가. 블록을 다시 세지는 않는다 — 센 값 자체는
     * 옛 루프도 같이 믿는 값이고(ChunkSectionRandomTickingTest·RandomTickMaskTest 가 칸 전수와 대조), 틱마다 세면 노이즈 지형
     * 벤치에서 region 틱이 수십 배가 된다(벤치도 -ea).
     */
    boolean knownNotTicking(ChunkColumn col) {
        return owner == col && !maybeTicking(tickingCount, fluidCount);
    }

    /** 시험용: 지금 블록을 다시 센 무작위 틱 칸 수(캐시를 건드리지 않음). 표가 없으면 -1. */
    int recountTicking() {
        boolean[] t = randomTicking;
        return t == null ? -1 : countTicking(t);
    }

    /** 시험용: 센 값(블록 수, 유체 수) — 모르면 블록 수 -1. */
    int[] countsForTest() { return new int[] {tickingCount == UNKNOWN ? -1 : tickingCount, fluidCount}; }

    public ChunkSection() {
        this(DEFAULT_DIRECT_BITS, DEFAULT_BIOME_DIRECT_BITS);
    }

    public ChunkSection(int directBits, int biomeDirectBits) {
        this.blocks = new PaletteStorage(4096, 4, 8, directBits, AIR);
        this.biomes = new PaletteStorage(64, 1, 3, biomeDirectBits, 0);
    }

    private ChunkSection(PaletteStorage blocks, PaletteStorage biomes, short nonAirCount, int[] scratch) {
        this.blocks = blocks;
        this.biomes = biomes;
        this.nonAirCount = nonAirCount;
        this.scratch = scratch;
    }

    /** 독립된 backing 저장소를 가진 깊은 복사본(pristine 청크 캐시용, ChunkPipeline 참조). */
    ChunkSection copy() {
        ChunkSection c = new ChunkSection(blocks.copy(), biomes.copy(), nonAirCount, scratch == null ? null : scratch.clone());
        c.tickingCount = tickingCount;
        c.fluidCount = fluidCount;
        c.nonEmpty = nonEmpty;
        return c;
    }

    /**
     * 이 객체의 블록을 읽는 빛 계산 수(T-064 빛 작업, T-197 relight). 0 이 아니면 컬럼은 쓰기 전에 복사본으로 갈아 끼운다(ChunkColumn.ownSection) —
     * 얼린 객체의 블록은 모두 녹일 때까지 바뀌지 않는다. 얼리기는 주인 region 스레드, 녹이기는 계산이 끝난 뒤 아무 스레드(나누기 뒤 새 주인이 아닐 수
     * 있다)라 원자 수다. 주인이 낡은 0 아닌 값을 보면 복사가 한 번 더 생길 뿐이다.
     */
    private volatile int lightFrozen;
    private static final java.lang.invoke.VarHandle LIGHT_FROZEN;
    static {
        try {
            LIGHT_FROZEN = java.lang.invoke.MethodHandles.lookup().findVarHandle(ChunkSection.class, "lightFrozen", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 빛 계산이 읽는 중인가(쓰기 전에 복사해야 하는가). */
    boolean lightFrozen() { return lightFrozen != 0; }

    /** 빛 계산 하나가 읽기 시작(주인 스레드). */
    void freezeLight() { LIGHT_FROZEN.getAndAdd(this, 1); }

    /** 빛 계산 하나가 끝남(아무 스레드). */
    void thawLight() {
        int before = (int) LIGHT_FROZEN.getAndAdd(this, -1);
        assert before > 0 : "얼리지 않은 구역을 녹임";
    }

    private static int blockIndex(int x, int y, int z) {
        return y * 256 + z * 16 + x;
    }

    private static int biomeIndex(int x, int y, int z) {
        return y * 16 + z * 4 + x;
    }

    public void setBlock(int x, int y, int z, int stateId) {
        setBlockAt(blockIndex(x, y, z), stateId, true);
    }

    /**
     * 칸 하나(섹션 로컬 인덱스 y*256+z*16+x)를 바꾸고 옛 상태를 돌려준다. Furnace 비공기 수는 늘 정확하게 고친다. vanillaCounts 면 바닐라
     * LevelChunkSection.setBlockState 처럼 개수 셋을 ±1(유체는 비어 있지 않으면 물도), 아니면 바닐라 블록 컨테이너 set 처럼 개수를 그대로 둔다(N2b-2).
     */
    int setBlockAt(int idx, int stateId, boolean vanillaCounts) {
        assert lightFrozen == 0 : "빛 작업이 읽는 구역을 그 자리에서 고침";
        version++;
        int old = scratch != null ? scratch[idx] : blocks.get(idx);
        boolean wasAir = isAir(old), nowAir = isAir(stateId);
        int tc = tickingCount;
        boolean[] t = randomTicking;
        // 모르면 쓰기 전 블록으로 바닐라 재계수 값부터 정한다 — 그 위에 ±1 이 쌓여야(또는 그대로 남아야) 바닐라 열과 같다.
        // scratch 가 열려 있으면 바깥이 직접 고치므로 건드리지 않는다(닫을 때 다시 센다). 표가 없는 시험은 개수 쪽을 모르는 채로 둔다.
        if (tc == UNKNOWN && scratch == null && (t != null || !vanillaCounts)) {
            recount(t);
            tc = tickingCount;
        }
        if (tc != UNKNOWN && vanillaCounts) {
            int n = tc;
            // 표 밖 상태 번호(시험이 아무 숫자나 쓴다)는 무작위 틱 아님·유체 없음으로 본다
            if (t != null && old < t.length && t[old]) n--;
            if (t != null && stateId < t.length && t[stateId]) n++;
            int fc = fluidCount, f = fc;
            boolean[] fp = fluidPresent;
            if (fp != null) {
                if (old < fp.length && fp[old]) f--;
                if (stateId < fp.length && fp[stateId]) f++;
            }
            if (!wasAir) nonEmpty--;
            if (!nowAir) nonEmpty++;
            if (n != tc || f != fc) counts(n, f);
        }
        // 비공기 수는 재계수 뒤에 고친다 — 재계수가 쓰기 전 블록과 쓰기 전 비공기 수를 함께 봐야 한다
        if (wasAir && !nowAir) nonAirCount++;
        else if (!wasAir && nowAir) nonAirCount--;
        if (scratch != null) scratch[idx] = stateId;
        else blocks.set(idx, stateId);
        return old;
    }

    public int getBlock(int x, int y, int z) {
        int idx = blockIndex(x, y, z);
        return scratch != null ? scratch[idx] : blocks.get(idx);
    }

    /** 섹션 로컬 인덱스(y*256+z*16+x)의 블록. scratch 를 열지 않는다. */
    int blockAt(int idx) {
        return scratch != null ? scratch[idx] : blocks.get(idx);
    }

    /** 모든 칸이 한 값이면 그 값, 아니면(또는 scratch 가 열려 있으면) -1. */
    int uniformBlock() {
        return scratch != null ? -1 : blocks.singleValueOr(-1);
    }

    /** 그 상태가 있을 수도 있는가(팔레트만 본다, scratch 가 열려 있으면 true). false 면 확실히 없다. */
    boolean maybeHasBlock(java.util.function.IntPredicate p) {
        return scratch != null || blocks.maybeHas(p);
    }

    public void fillBiome(int biomeId) {
        version++;
        for (int i = 0; i < 64; i++) biomes.set(i, biomeId);
    }

    public int getBiome(int x, int y, int z) {
        return biomes.get(biomeIndex(x, y, z));
    }

    public void setBiome(int x, int y, int z, int biomeId) {
        version++;
        biomes.set(biomeIndex(x, y, z), biomeId);
    }

    /** 바이옴 칸 하나(섹션 로컬 인덱스 y*16+z*4+x)를 바꾸고 옛 값을 돌려준다(바닐라 바이옴 컨테이너 getAndSet). */
    int setBiomeAt(int idx, int biomeId) {
        version++;
        int old = biomes.get(idx);
        biomes.set(idx, biomeId);
        return old;
    }

    public void replaceBlocks(int[] source, int sourceOffset) {
        assert lightFrozen == 0 : "빛 작업이 읽는 구역을 그 자리에서 고침";
        version++;
        countsUnknown();
        if (scratch != null) {
            System.arraycopy(source, sourceOffset, scratch, 0, 4096);
        } else {
            blocks.load(source, sourceOffset);
        }
        int count = 0;
        for (int i = 0; i < 4096; i++) if (!isAir(source[sourceOffset + i])) count++;
        nonAirCount = (short) count;
    }

    /** replaceBlocks(펼친 배열) 과 같은 결과를 팔레트 값 + 칸별 인덱스에서. scratch 가 열려 있으면 펼쳐서 예전 경로. */
    void replaceBlocksIndexed(int[] paletteVals, BitStorage indices) {
        assert lightFrozen == 0 : "빛 작업이 읽는 구역을 그 자리에서 고침";
        if (scratch != null) {
            int[] flat = new int[4096];
            for (int i = 0; i < 4096; i++) flat[i] = paletteVals[indices.get(i)];
            replaceBlocks(flat, 0);
            return;
        }
        version++;
        countsUnknown();
        nonAirCount = (short) blocks.loadIndexed(paletteVals, indices);
    }

    public void replaceBiomes(int[] source, int sourceOffset) {
        version++;
        biomes.load(source, sourceOffset);
    }

    /** 비공기 수가 0 인가. scratch 가 열린 동안에는 바깥이 scratch 를 직접 고쳐 낡을 수 있다(닫을 때 compact 가 다시 센다) — scratchOpen 참고. */
    public boolean hasOnlyAir() { return nonAirCount == 0; }

    /** 지금 블록이 모두 공기인가(바닐라 hasOnlyAir) — scratch 가 열려 있어도 정확하다(그때는 칸을 훑는다). */
    public boolean onlyAirExact() {
        int[] s = scratch;   // 한 번만 읽는다
        if (s == null) return nonAirCount == 0;
        for (int v : s) if (!isAir(v)) return false;
        return true;
    }

    /** scratch(펼친 배열)가 열려 있는가 — 열려 있으면 hasOnlyAir 를 믿지 말고 칸을 읽는다. */
    public boolean scratchOpen() { return scratch != null; }

    public short nonAirCount() {
        return nonAirCount;
    }

    public int[] blocks() {
        return scratch != null ? scratch.clone() : blocks.unpack();
    }

    public int[] biomes() {
        return biomes.unpack();
    }

    /** 스냅샷용 블록 사본: 팔레트 원본 그대로(풀지 않음). scratch 가 열려 있으면 null — 그때는 {@link #blocks()} 를 쓴다. */
    PaletteStorage blocksReadCopy() {
        return scratch != null ? null : blocks.readCopy();
    }

    /** 스냅샷용 바이옴 사본(팔레트 원본 그대로). */
    PaletteStorage biomesReadCopy() {
        return biomes.readCopy();
    }

    int[] openScratch() {
        assert lightFrozen == 0 : "빛 작업이 읽는 구역을 그 자리에서 고침";
        version++;
        countsUnknown();   // 바깥에서 scratch 를 직접 고친다
        if (scratch == null) scratch = blocks.unpack();
        return scratch;
    }

    void compact() {
        if (scratch == null) return;
        assert lightFrozen == 0 : "빛 작업이 읽는 구역을 그 자리에서 고침";
        version++;
        countsUnknown();
        blocks.load(scratch, 0);
        int count = 0;
        for (int value : scratch) if (!isAir(value)) count++;   // 바닐라 recalcBlockCounts 처럼 cave_air·void_air 도 공기
        nonAirCount = (short) count;
        scratch = null;
    }
}
