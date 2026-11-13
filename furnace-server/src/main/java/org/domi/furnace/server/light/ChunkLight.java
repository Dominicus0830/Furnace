// 청크 하나의 빛 데이터: 하늘빛·블록빛 섹션별 니블 배열(바닐라 DataLayer — 아래 -5 섹션부터 위 20 섹션까지 26개)과 열마다 하늘빛 원천 높이.
package org.domi.furnace.server.light;

public final class ChunkLight {
    public static final int MIN_SECTION = -5, SECTIONS = 26;
    public static final int MIN_Y = MIN_SECTION * 16;                 // -80
    public static final int MAX_Y = (MIN_SECTION + SECTIONS) * 16;    // 336 (배타)
    /** 열에서 막는 블록이 하나도 없을 때의 값(월드 바닥 − 1). 바깥에는 Integer.MIN_VALUE(모든 높이가 원천)로 보인다. */
    public static final int NO_BLOCKER = -65;

    public final byte[][] sky;
    public final byte[][] block;
    /** 열(x + z*16)마다 가장 낮은 하늘빛 원천 y(그 y 이상은 모두 원천). */
    public final int[] lowestSource;
    /** setLightEnabled — 켜진 청크만 빛이 있다. */
    public boolean lightEnabled;

    /**
     * 빛 작업용 쓰기 사본(T-064)이면 그 원본(보이는 쪽), 아니면 null. 사본의 층은 처음엔 원본 배열을 그대로 가리키고, 처음 쓸 때 복사한다
     * (바닐라 DataLayerStorageMap.copyDataLayer). 원본은 빛 작업이 도는 동안 아무도 고치지 않는다 — 게시 때 region 스레드가 바꿔 끼운다.
     */
    private final ChunkLight base;
    private long ownSky, ownBlock;   // 사본이 복사해 가진 층(비트 = 층 번호)

    public ChunkLight() {
        sky = new byte[SECTIONS][];
        block = new byte[SECTIONS][];
        lowestSource = new int[256];
        base = null;
        for (int i = 0; i < SECTIONS; i++) {
            sky[i] = new byte[2048];
            block[i] = new byte[2048];
        }
        java.util.Arrays.fill(lowestSource, NO_BLOCKER);
    }

    private ChunkLight(ChunkLight base) {
        this.base = base;
        this.sky = base.sky.clone();
        this.block = base.block.clone();
        this.lowestSource = base.lowestSource.clone();
        this.lightEnabled = base.lightEnabled;
    }

    /** 빛 작업용 쓰기 사본: 층은 처음 쓸 때 복사하고, 원천 높이·켜짐은 사본이 따로 가진다. 원본을 가진 스레드에서 만든다. */
    public static ChunkLight writeCopyOf(ChunkLight visible) { return new ChunkLight(visible); }

    /** 이 층 배열(sky 또는 block)의 idx 층에 쓸 배열. 쓰기 사본이면 처음 쓸 때 원본을 복사해 둔다. */
    public byte[] writable(byte[][] layers, int idx) {
        if (base == null) return layers[idx];
        boolean isSky = layers == sky;
        long bit = 1L << idx;
        if (((isSky ? ownSky : ownBlock) & bit) == 0) {
            layers[idx] = layers[idx].clone();
            if (isSky) ownSky |= bit;
            else ownBlock |= bit;
        }
        return layers[idx];
    }

    /** 쓰기 사본이 바꾼 것(복사한 층·원천 높이·켜짐)을 원본에 넣는다 — 원본을 가진 스레드에서, 빛 작업이 끝난 뒤. */
    public void publishTo(ChunkLight visible) {
        for (int i = 0; i < SECTIONS; i++) {
            if ((ownSky & 1L << i) != 0) visible.sky[i] = sky[i];
            if ((ownBlock & 1L << i) != 0) visible.block[i] = block[i];
        }
        System.arraycopy(lowestSource, 0, visible.lowestSource, 0, 256);
        visible.lightEnabled = lightEnabled;
    }

    /** 이 사본의 원본(쓰기 사본이 아니면 null). */
    public ChunkLight base() { return base; }

    public static int sectionIndex(int y) { return (y >> 4) - MIN_SECTION; }

    private static int index(int x, int y, int z) { return (y & 15) << 8 | (z & 15) << 4 | (x & 15); }

    public static int get(byte[] layer, int x, int y, int z) {
        int i = index(x, y, z);
        int b = layer[i >> 1];
        return (i & 1) == 0 ? b & 15 : b >> 4 & 15;
    }

    public static void set(byte[] layer, int x, int y, int z, int v) {
        int i = index(x, y, z);
        int b = layer[i >> 1];
        layer[i >> 1] = (byte) ((i & 1) == 0 ? b & 0xF0 | v & 15 : b & 15 | (v & 15) << 4);
    }

    public int skyAt(int x, int y, int z) { return get(sky[sectionIndex(y)], x, y, z); }

    public int blockAt(int x, int y, int z) { return get(block[sectionIndex(y)], x, y, z); }
}
