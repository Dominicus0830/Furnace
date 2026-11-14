// 청크 섹션: 블록 16³ + 바이옴 4³ palette 저장 + 비공기 수. 인덱스는 바닐라 YZX 순.
package org.domi.furnace.server.world;

public final class ChunkSection {

    public static final int AIR = 0;
    /** 1.21.1 의 공기 상태(air·void_air·cave_air) — 바닐라 nonEmptyBlockCount 는 isAir 가 아닌 칸만 센다. */
    private static final int VOID_AIR = 12958, CAVE_AIR = 12959;

    public static boolean isAir(int stateId) { return stateId == AIR || stateId == VOID_AIR || stateId == CAVE_AIR; }
    private static final int DEFAULT_DIRECT_BITS = 15;
    private static final int DEFAULT_BIOME_DIRECT_BITS = 7;

    private final PaletteStorage blocks;
    private final PaletteStorage biomes;
    private short nonAirCount = 0;
    private int[] scratch;
    /** 무작위 틱 받는 칸 수(바닐라 tickingBlockCount — 블록 또는 그 유체가 무작위 틱). -1 이면 다시 세야 한다. */
    private int tickingCount = -1;
    /**
     * 바닐라 tickingFluidCount. 칸 쓰기(setBlockState)는 비어 있지 않은 유체(물 포함)를 ±1 하지만, 재계수(recalcBlockCounts — 적재·생성 결과 통째 넣기)는
     * 무작위 틱 받는 유체(용암)만 센다. 그래서 양동이로 물을 놓은 구역은 대상이 되고 디스크에서 다시 읽은 구역은 아니다(바닐라 그대로). 칸 쓰기로 음수가 될 수 있다.
     * tickingCount 가 -1(모름) 이면 뜻이 없다 — 다시 셀 때 함께 정한다.
     */
    private int fluidCount;
    /**
     * 이 구역을 배열에 가진 컬럼과 그 자리(T-053 무작위 틱 구역 마스크). tickingCount 가 0 ↔ 0 아님으로 바뀔 때 컬럼 마스크 비트를 고친다.
     * cowCopy 가 공유하는 쪽 컬럼은 주인이 아니다 — 그 컬럼은 공유 구역의 비트를 켜 둔다. 컬럼 밖(시험·빛 작업이 쥔 옛 객체)이면 null.
     */
    private ChunkColumn owner;
    private int ownerIndex;

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
        if (known >= 0) return known > 0 || fluidCount > 0;   // 대부분 이미 센 값이다 — 표(volatile)를 읽을 필요 없다(X1)
        boolean[] t = randomTicking;
        if (t == null) return false;
        recount(t);
        return tickingCount > 0 || fluidCount > 0;
    }

    /** 지금 블록에서 무작위 틱 받는 칸 수를 센다(캐시를 건드리지 않음). */
    private int countTicking(boolean[] t) {
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

    /** 바닐라 recalcBlockCounts: 두 센 값을 지금 블록에서 다시 정한다. */
    private void recount(boolean[] t) {
        counts(countTicking(t), countLava(fluidLava));
    }

    /** 센 값 둘을 바꾸고, "대상일 수 있음"(블록 수 ≠ 0(-1 = 모름 포함) 또는 유체 수 > 0)이 바뀌었으면 주인 컬럼의 마스크 비트를 고친다(T-053). */
    private void counts(int blockN, int fluidN) {
        boolean was = tickingCount != 0 || fluidCount > 0;
        tickingCount = blockN;
        fluidCount = fluidN;
        boolean now = blockN != 0 || fluidN > 0;
        if (was != now && owner != null) owner.sectionTickingChanged(ownerIndex, now);
    }

    /** 블록 수를 -1(모름)로 — 바깥이 블록을 통째로 바꾼다. 다시 셀 때 유체 수도 새로 정한다. */
    private void ticking(int n) {
        counts(n, 0);
    }

    /** 이 구역을 컬럼 자리 i 에 놓는다 — 주인을 정하고 그 비트를 지금 값에 맞춘다. ChunkColumn 이 배열에 넣을 때만 부른다. */
    void attach(ChunkColumn col, int i) {
        owner = col;
        ownerIndex = i;
        col.sectionTickingChanged(i, tickingCount != 0 || fluidCount > 0);
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
        return owner == col && tickingCount == 0 && fluidCount <= 0;
    }

    /** 시험용: 지금 블록을 다시 센 무작위 틱 칸 수(캐시를 건드리지 않음). 표가 없으면 -1. */
    int recountTicking() {
        boolean[] t = randomTicking;
        return t == null ? -1 : countTicking(t);
    }

    /** 시험용: 센 값(블록 수, 유체 수) — 블록 수 -1 이면 모름. */
    int[] countsForTest() { return new int[] {tickingCount, fluidCount}; }

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
        return c;
    }

    /**
     * 빛 작업(T-064)이 이 객체의 블록을 읽는 중이다. 컬럼은 얼린 구역에 쓰기 전에 복사본으로 갈아 끼운다(ChunkColumn.ownSection) —
     * 얼린 객체의 블록은 녹일 때까지 바뀌지 않는다. 주인 region 스레드만 읽고 쓴다(빛 작업은 보지 않는다).
     */
    boolean lightFrozen;

    private static int blockIndex(int x, int y, int z) {
        return y * 256 + z * 16 + x;
    }

    private static int biomeIndex(int x, int y, int z) {
        return y * 16 + z * 4 + x;
    }

    public void setBlock(int x, int y, int z, int stateId) {
        assert !lightFrozen : "빛 작업이 읽는 구역을 그 자리에서 고침";
        int idx = blockIndex(x, y, z);
        int old = scratch != null ? scratch[idx] : blocks.get(idx);
        boolean wasAir = isAir(old), nowAir = isAir(stateId);
        if (wasAir && !nowAir) nonAirCount++;
        else if (!wasAir && nowAir) nonAirCount--;
        int tc = tickingCount;
        boolean[] t = randomTicking;
        if (tc < 0 && t != null && scratch == null) {   // 모르면(scratch 가 열려 있으면 바깥이 직접 고치므로 건드리지 않는다) 쓰기 전 블록으로 바닐라 재계수 값부터 정한다 — 그 위에 ±1 이 쌓여야 바닐라 열과 같다
            recount(t);
            tc = tickingCount;
        }
        if (tc >= 0) {
            int n = tc;
            // 표 밖 상태 번호(시험이 아무 숫자나 쓴다)는 무작위 틱 아님·유체 없음으로 본다
            if (old < t.length && t[old]) n--;
            if (stateId < t.length && t[stateId]) n++;
            int fc = fluidCount, f = fc;
            boolean[] fp = fluidPresent;
            if (fp != null) {
                if (old < fp.length && fp[old]) f--;
                if (stateId < fp.length && fp[stateId]) f++;
            }
            if (n != tc || f != fc) counts(n, f);
        }
        if (scratch != null) scratch[idx] = stateId;
        else blocks.set(idx, stateId);
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
        for (int i = 0; i < 64; i++) biomes.set(i, biomeId);
    }

    public int getBiome(int x, int y, int z) {
        return biomes.get(biomeIndex(x, y, z));
    }

    public void setBiome(int x, int y, int z, int biomeId) {
        biomes.set(biomeIndex(x, y, z), biomeId);
    }

    public void replaceBlocks(int[] source, int sourceOffset) {
        assert !lightFrozen : "빛 작업이 읽는 구역을 그 자리에서 고침";
        ticking(-1);
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
        assert !lightFrozen : "빛 작업이 읽는 구역을 그 자리에서 고침";
        if (scratch != null) {
            int[] flat = new int[4096];
            for (int i = 0; i < 4096; i++) flat[i] = paletteVals[indices.get(i)];
            replaceBlocks(flat, 0);
            return;
        }
        ticking(-1);
        nonAirCount = (short) blocks.loadIndexed(paletteVals, indices);
    }

    public void replaceBiomes(int[] source, int sourceOffset) {
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
        assert !lightFrozen : "빛 작업이 읽는 구역을 그 자리에서 고침";
        ticking(-1);   // 바깥에서 scratch 를 직접 고친다
        if (scratch == null) scratch = blocks.unpack();
        return scratch;
    }

    void compact() {
        if (scratch == null) return;
        assert !lightFrozen : "빛 작업이 읽는 구역을 그 자리에서 고침";
        ticking(-1);
        blocks.load(scratch, 0);
        int count = 0;
        for (int value : scratch) if (!isAir(value)) count++;   // 바닐라 recalcBlockCounts 처럼 cave_air·void_air 도 공기
        nonAirCount = (short) count;
        scratch = null;
    }
}
