// 청크 하나의 빛 데이터: 하늘빛·블록빛 섹션별 니블 배열(바닐라 DataLayer — 아래 -5 섹션부터 위 20 섹션까지 26개)과 열마다 하늘빛 원천 높이.
package org.domi.furnace.server.light;

public final class ChunkLight {
    public static final int MIN_SECTION = -5, SECTIONS = 26;
    public static final int MIN_Y = MIN_SECTION * 16;                 // -80
    public static final int MAX_Y = (MIN_SECTION + SECTIONS) * 16;    // 336 (배타)
    /** 열에서 막는 블록이 하나도 없을 때의 값(월드 바닥 − 1). 바깥에는 Integer.MIN_VALUE(모든 높이가 원천)로 보인다. */
    public static final int NO_BLOCKER = -65;

    public final byte[][] sky = new byte[SECTIONS][];
    public final byte[][] block = new byte[SECTIONS][];
    /** 열(x + z*16)마다 가장 낮은 하늘빛 원천 y(그 y 이상은 모두 원천). */
    public final int[] lowestSource = new int[256];
    /** setLightEnabled — 켜진 청크만 빛이 있다. */
    public boolean lightEnabled;

    public ChunkLight() {
        for (int i = 0; i < SECTIONS; i++) {
            sky[i] = new byte[2048];
            block[i] = new byte[2048];
        }
        java.util.Arrays.fill(lowestSource, NO_BLOCKER);
    }

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
