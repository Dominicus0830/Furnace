// 사용 중 섹터 비트맵. 바닐라 1.21.1 RegionBitmap(난독 dwo)과 같은 첫 적합 알고리즘(F-05).
package org.domi.furnace.server.world.anvil;

import java.util.BitSet;

final class SectorBitmap {
    private final BitSet bits = new BitSet();

    void set(int start, int count) { bits.set(start, start + count); }

    void clear(int start, int count) { bits.clear(start, start + count); }

    boolean anySet(int start, int count) {
        int n = bits.nextSetBit(start);
        return n != -1 && n < start + count;
    }

    /** 앞에서부터 count 개 연속 빈 섹터를 찾아 표시하고 시작을 돌려준다(없으면 끝 뒤). */
    int allocate(int count) {
        int from = 0;
        while (true) {
            int clear = bits.nextClearBit(from);
            int nextSet = bits.nextSetBit(clear);
            if (nextSet == -1 || nextSet - clear >= count) {
                set(clear, count);
                return clear;
            }
            from = nextSet;
        }
    }

    BitSet copy() { return (BitSet) bits.clone(); }
}
