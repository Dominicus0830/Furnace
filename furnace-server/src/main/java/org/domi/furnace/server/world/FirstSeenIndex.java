// T-015: 묶인 팔레트(PaletteStorage)를 int[4096] 으로 펼치지 않고, 와이어·디스크가 쓰는 "첫 등장 순" 팔레트로 다시 매긴다.
package org.domi.furnace.server.world;

import java.util.Arrays;

/**
 * 칸 배열을 펼친 뒤 값마다 해시하던 옛 경로(Palette.write·AnvilChunkCodec.FirstSeen)와 같은 결과를 낸다:
 * {@link #size()} = 서로 다른 값 수, {@link #value(int)} = 첫 등장 순 값, {@link #pack(int)} = 칸별 첫 등장 번호를 bits 비트로 묶은 long[].
 * 저장소의 팔레트 번호 → 첫 등장 번호 표(최대 256칸)만 쓰므로 칸마다 해시가 없고, 저장소 팔레트가 이미 첫 등장 순이며
 * 비트 수가 같으면 묶인 배열을 그대로 돌려준다(복사 없음).
 *
 * <p>스레드마다 블록용·바이옴용 하나씩({@link #forBlocks()}·{@link #forBiomes()})이라 블록 색인을 쥔 채 바이옴 색인을 불러도 된다.
 * 같은 종류의 다음 색인 호출 전까지만 유효하다. 재진입하지 않는다.
 */
public final class FirstSeenIndex {

    private static final ThreadLocal<FirstSeenIndex> BLOCKS = ThreadLocal.withInitial(FirstSeenIndex::new);
    private static final ThreadLocal<FirstSeenIndex> BIOMES = ThreadLocal.withInitial(FirstSeenIndex::new);

    static FirstSeenIndex forBlocks() { return BLOCKS.get(); }

    static FirstSeenIndex forBiomes() { return BIOMES.get(); }

    /** 간접 저장소 비트 수 상한은 8(블록) — 팔레트 번호는 256 미만. */
    private final int[] remap = new int[256];
    private final int[] values = new int[256];
    private int n;
    private int cells;
    /**
     * 원본 묶인 배열(단일값이면 null)과 그 비트 수. 읽기 사본의 배열이라 고치지 않는다. 다 쓴 뒤에도 다음 색인 때까지 붙잡는다
     * (스레드·종류당 최대 2KB — pack 이 공유 갈래로 돌려준 배열을 받은 쪽이 쓰는 동안 비울 이유가 없다).
     */
    private long[] src;
    private int srcBits;
    /** 쓰인 저장소 번호가 정확히 0..n-1 이고 칸마다 번호 그대로인가(첫 등장 순 = 저장 순). */
    private boolean identity;

    private FirstSeenIndex() {}

    /** 서로 다른 값 수(1 이상). */
    public int size() { return n; }

    /** 첫 등장 순 i 번째 값. */
    public int value(int i) {
        if (i >= n) throw new IndexOutOfBoundsException(i);
        return values[i];
    }

    /** 단일값(모든 칸이 한 값). */
    void single(int value, int size) {
        n = 1;
        values[0] = value;
        cells = size;
        src = null;
        srcBits = 0;
        identity = true;
    }

    /**
     * 간접 저장소(팔레트 + 번호 배열)를 훑는다. 칸 번호를 long 째로 풀어 저장 번호 → 첫 등장 번호 표를 채운다.
     * 쓰이지 않는 옛 팔레트 항목은 빠진다(옛 경로는 칸 값만 보므로 같다). 저장소 팔레트 값은 서로 겹치지 않는다는 불변식에
     * 기댄다(PaletteStorage.idFor 가 찾은 뒤 덧붙이고, loadIndexed 는 겹치는 NBT 값을 합친다) — 그래서 번호가 다르면 값도 다르다.
     */
    void indirect(int[] palette, long[] data, int bits, int size) {
        int[] rm = remap;
        Arrays.fill(rm, 0, 1 << bits, -1);
        int vpl = 64 / bits;
        long mask = (1L << bits) - 1;
        int count = 0;
        boolean id = true;
        int cell = 0;
        for (int w = 0; cell < size; w++) {
            long word = data[w];
            int end = Math.min(size, cell + vpl);
            for (; cell < end; cell++, word >>>= bits) {
                int k = (int) (word & mask);
                if (rm[k] < 0) {
                    if (k != count) id = false;
                    rm[k] = count;
                    values[count++] = palette[k];
                }
            }
        }
        n = count;
        cells = size;
        src = data;
        srcBits = bits;
        identity = id;
        assert distinct(values, count) : "저장소 팔레트에 같은 값이 둘 — 첫 등장 순 색인이 옛 경로와 달라진다";
    }

    /** 불변식 가드(-ea): 값이 서로 겹치지 않는가. n ≤ 256. */
    private static boolean distinct(int[] v, int n) {
        for (int i = 1; i < n; i++) for (int j = 0; j < i; j++) if (v[i] == v[j]) return false;
        return true;
    }

    /**
     * 칸별 첫 등장 번호를 bits 비트로 묶는다(BitStorage 와 같은 배치: 한 long 에 64/bits 개, long 경계를 넘지 않음, 남는 비트 0).
     * 저장 순이 첫 등장 순이고 비트 수가 같으면 원본 배열을 그대로 돌려준다 — 받은 쪽은 고치지 않는다.
     */
    public long[] pack(int bits) {
        if (n <= 1) throw new IllegalStateException("단일값은 묶을 칸 번호가 없다");
        if (identity && bits == srcBits) return src;
        int inVpl = 64 / srcBits, outVpl = 64 / bits;
        long inMask = (1L << srcBits) - 1;
        long[] out = new long[(cells + outVpl - 1) / outVpl];
        int[] rm = remap;
        int cell = 0, oi = 0, oshift = 0;
        long acc = 0;
        for (int w = 0; cell < cells; w++) {
            long word = src[w];
            int end = Math.min(cells, cell + inVpl);
            for (; cell < end; cell++, word >>>= srcBits) {
                acc |= (long) rm[(int) (word & inMask)] << oshift;
                oshift += bits;
                if (oshift + bits > 64) {   // 이 long 에 더 못 넣는다(64/bits 개 채움)
                    out[oi++] = acc;
                    acc = 0;
                    oshift = 0;
                }
            }
        }
        if (oshift != 0) out[oi] = acc;
        return out;
    }
}
