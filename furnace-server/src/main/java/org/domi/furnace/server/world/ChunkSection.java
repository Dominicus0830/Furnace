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

    /** 상태별 무작위 틱 여부(블록 동작 쪽이 건다). 없으면 무작위 틱을 하지 않는다. */
    private static volatile boolean[] randomTicking;

    public static void randomTickingTable(boolean[] table) { randomTicking = table; }

    /** LevelChunkSection.isRandomlyTicking: 무작위 틱 받는 칸이 하나라도 있는가. */
    public boolean isRandomlyTicking() {
        boolean[] t = randomTicking;
        if (t == null) return false;
        if (tickingCount < 0) {
            int n = 0;
            if (scratch != null) {
                for (int v : scratch) if (t[v]) n++;
            } else {
                for (int i = 0; i < 4096; i++) if (t[blocks.get(i)]) n++;
            }
            tickingCount = n;
        }
        return tickingCount > 0;
    }

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
        return c;
    }

    private static int blockIndex(int x, int y, int z) {
        return y * 256 + z * 16 + x;
    }

    private static int biomeIndex(int x, int y, int z) {
        return y * 16 + z * 4 + x;
    }

    public void setBlock(int x, int y, int z, int stateId) {
        int idx = blockIndex(x, y, z);
        int old = scratch != null ? scratch[idx] : blocks.get(idx);
        boolean wasAir = isAir(old), nowAir = isAir(stateId);
        if (wasAir && !nowAir) nonAirCount++;
        else if (!wasAir && nowAir) nonAirCount--;
        if (tickingCount >= 0) {
            boolean[] t = randomTicking;
            if (t[old]) tickingCount--;
            if (t[stateId]) tickingCount++;
        }
        if (scratch != null) scratch[idx] = stateId;
        else blocks.set(idx, stateId);
    }

    public int getBlock(int x, int y, int z) {
        int idx = blockIndex(x, y, z);
        return scratch != null ? scratch[idx] : blocks.get(idx);
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
        tickingCount = -1;
        if (scratch != null) {
            System.arraycopy(source, sourceOffset, scratch, 0, 4096);
        } else {
            blocks.load(source, sourceOffset);
        }
        int count = 0;
        for (int i = 0; i < 4096; i++) if (!isAir(source[sourceOffset + i])) count++;
        nonAirCount = (short) count;
    }

    public void replaceBiomes(int[] source, int sourceOffset) {
        biomes.load(source, sourceOffset);
    }

    public boolean hasOnlyAir() { return nonAirCount == 0; }

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
        tickingCount = -1;   // 바깥에서 scratch 를 직접 고친다
        if (scratch == null) scratch = blocks.unpack();
        return scratch;
    }

    void compact() {
        if (scratch == null) return;
        tickingCount = -1;
        blocks.load(scratch, 0);
        int count = 0;
        for (int value : scratch) if (value != AIR) count++;
        nonAirCount = (short) count;
        scratch = null;
    }
}
