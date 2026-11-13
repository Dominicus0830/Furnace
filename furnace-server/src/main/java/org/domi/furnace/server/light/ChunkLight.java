// 청크 하나의 빛 데이터: 하늘빛·블록빛 섹션별 니블 배열(바닐라 DataLayer — 아래 -5 섹션부터 위 20 섹션까지 26개)과 열마다 하늘빛 원천 높이.
//
// 층은 처음에 공유 상수 ZERO(모두 0)이고, 처음 쓸 때 복사한다(쓰기 시 복사 — T-012/O07-05). 모두 15 인 층은 FULL 을 공유한다.
// 공유 상수는 절대 고치지 않는다: 쓰기는 이 클래스의 writable*·set*·fill* 만 거치고, 밖에는 읽기용 층만 내준다(sky(i)·block(i)).
package org.domi.furnace.server.light;

import java.util.Arrays;

public final class ChunkLight {
    public static final int MIN_SECTION = -5, SECTIONS = 26;
    public static final int MIN_Y = MIN_SECTION * 16;                 // -80
    public static final int MAX_Y = (MIN_SECTION + SECTIONS) * 16;    // 336 (배타)
    /** 열에서 막는 블록이 하나도 없을 때의 값(월드 바닥 − 1). 바깥에는 Integer.MIN_VALUE(모든 높이가 원천)로 보인다. */
    public static final int NO_BLOCKER = -65;
    public static final int LAYER_BYTES = 2048;

    /** 모두 0 인 층(공유, 읽기 전용). */
    private static final byte[] ZERO = new byte[LAYER_BYTES];
    /** 모두 15 인 층(공유, 읽기 전용). */
    private static final byte[] FULL = new byte[LAYER_BYTES];
    static {
        Arrays.fill(FULL, (byte) 0xFF);
    }

    private final byte[][] sky = new byte[SECTIONS][];
    private final byte[][] block = new byte[SECTIONS][];
    /** 열(x + z*16)마다 가장 낮은 하늘빛 원천 y(그 y 이상은 모두 원천). */
    public final int[] lowestSource = new int[256];
    /** setLightEnabled — 켜진 청크만 빛이 있다. */
    public boolean lightEnabled;

    public ChunkLight() {
        Arrays.fill(sky, ZERO);
        Arrays.fill(block, ZERO);
        Arrays.fill(lowestSource, NO_BLOCKER);
    }

    /**
     * 저장된 층으로 만든다(디스크 빛). null 이거나 길이가 2048 이 아닌 층은 0. 균일 층(모두 0·모두 15)은 공유 상수로 바꾸고, 나머지 배열은
     * 그대로 넘겨받는다(부르는 쪽은 넘긴 배열을 더 쓰지 않는다). 하늘빛 원천은 비어 있다 — 블록을 보고 따로 센다.
     */
    public static ChunkLight fromLayers(byte[][] skyLayers, byte[][] blockLayers) {
        ChunkLight l = new ChunkLight();
        for (int i = 0; i < SECTIONS; i++) {
            l.sky[i] = adopt(skyLayers == null ? null : skyLayers[i]);
            l.block[i] = adopt(blockLayers == null ? null : blockLayers[i]);
        }
        return l;
    }

    private static byte[] adopt(byte[] a) {
        if (a == null || a.length != LAYER_BYTES) return ZERO;
        byte[] u = uniform(a);
        return u != null ? u : a;
    }

    /** 모두 0 이면 ZERO, 모두 0xFF 면 FULL, 아니면 null. */
    private static byte[] uniform(byte[] a) {
        if (a == ZERO || a == FULL) return a;
        byte first = a[0];
        if (first != 0 && first != (byte) 0xFF) return null;
        for (int i = 1; i < LAYER_BYTES; i++) if (a[i] != first) return null;
        return first == 0 ? ZERO : FULL;
    }

    /** 공유 상수(ZERO·FULL)인가 — 이 층은 고치면 안 된다. */
    public static boolean isShared(byte[] layer) { return layer == ZERO || layer == FULL; }

    /** 모두 0 이 확실한가(공유 ZERO). false 여도 모두 0 일 수 있다. */
    public static boolean isKnownZero(byte[] layer) { return layer == ZERO; }

    /** 모두 15 가 확실한가(공유 FULL). */
    public static boolean isKnownFull(byte[] layer) { return layer == FULL; }

    /** 읽기용 하늘빛 층(공유 상수일 수 있다 — 고치지 말 것). */
    public byte[] sky(int section) { return sky[section]; }

    /** 읽기용 블록빛 층(공유 상수일 수 있다 — 고치지 말 것). */
    public byte[] block(int section) { return block[section]; }

    /** 쓸 하늘빛 층(공유면 이 청크 몫으로 복사). */
    public byte[] writableSky(int section) {
        byte[] l = sky[section];
        if (isShared(l)) sky[section] = l = l.clone();
        return l;
    }

    /** 쓸 블록빛 층(공유면 이 청크 몫으로 복사). */
    public byte[] writableBlock(int section) {
        byte[] l = block[section];
        if (isShared(l)) block[section] = l = l.clone();
        return l;
    }

    /** 하늘빛 층 전체를 15 로(SkyLightEngine 의 Arrays.fill 0xFF). */
    public void fillSkyFull(int section) { sky[section] = FULL; }

    /** 균일해진 층을 공유 상수로 되돌린다(내용은 그대로). 생성 풀의 미리 계산 끝에 부른다. */
    public void compact() {
        for (int i = 0; i < SECTIONS; i++) {
            byte[] s = uniform(sky[i]), b = uniform(block[i]);
            if (s != null) sky[i] = s;
            if (b != null) block[i] = b;
        }
        assert sharedIntact() : "공유 빛 층이 바뀌었다";
    }

    /** 공유 상수가 처음 내용 그대로인가(시험·-ea 점검용). */
    public static boolean sharedIntact() {
        for (int i = 0; i < LAYER_BYTES; i++) if (ZERO[i] != 0 || FULL[i] != (byte) 0xFF) return false;
        return true;
    }

    /** 공유가 아닌(이 청크만 가진) 층 수 — 메모리 측정용. */
    public int ownedLayers() {
        int n = 0;
        for (int i = 0; i < SECTIONS; i++) {
            if (!isShared(sky[i])) n++;
            if (!isShared(block[i])) n++;
        }
        return n;
    }

    public static int sectionIndex(int y) { return (y >> 4) - MIN_SECTION; }

    private static int index(int x, int y, int z) { return (y & 15) << 8 | (z & 15) << 4 | (x & 15); }

    public static int get(byte[] layer, int x, int y, int z) {
        int i = index(x, y, z);
        int b = layer[i >> 1];
        return (i & 1) == 0 ? b & 15 : b >> 4 & 15;
    }

    /** 층 하나의 칸에 쓴다 — layer 는 writable* 로 얻은 것이어야 한다. */
    public static void set(byte[] layer, int x, int y, int z, int v) {
        assert !isShared(layer) : "공유 빛 층에 쓰기";
        int i = index(x, y, z);
        int b = layer[i >> 1];
        layer[i >> 1] = (byte) ((i & 1) == 0 ? b & 0xF0 | v & 15 : b & 15 | (v & 15) << 4);
    }

    public int skyAt(int x, int y, int z) { return get(sky[sectionIndex(y)], x, y, z); }

    public int blockAt(int x, int y, int z) { return get(block[sectionIndex(y)], x, y, z); }

    /** 하늘빛 한 칸 쓰기(층이 공유면 복사). */
    public void setSky(int x, int y, int z, int v) { set(writableSky(sectionIndex(y)), x, y, z, v); }

    /** 블록빛 한 칸 쓰기(층이 공유면 복사). */
    public void setBlock(int x, int y, int z, int v) { set(writableBlock(sectionIndex(y)), x, y, z, v); }
}
