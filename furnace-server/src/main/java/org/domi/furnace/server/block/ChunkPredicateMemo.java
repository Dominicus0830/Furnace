// 청크 판정(틱 범위 등)을 한 단계 안에서 기억한다 — 같은 청크의 예약 틱·블록 엔티티 수천 개가 판정(티켓·플레이어 거리)을 청크당 한 번만 부르게.
// 키는 (청크 x << 32 | 청크 z). 64칸 직접 사상이라 충돌하면 다시 부를 뿐 결과는 같다.
package org.domi.furnace.server.block;

import java.util.Arrays;
import java.util.function.LongPredicate;

public final class ChunkPredicateMemo implements LongPredicate {

    private final long[] key = new long[64];
    private final boolean[] val = new boolean[64], used = new boolean[64];
    private LongPredicate predicate = k -> true;

    /** 새 단계: 기억을 비우고 판정을 바꾼다. */
    public ChunkPredicateMemo reset(LongPredicate predicate) {
        this.predicate = predicate;
        Arrays.fill(used, false);
        return this;
    }

    public static long chunkKey(int chunkX, int chunkZ) { return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL; }

    /** 칸(Pos.asLong)의 청크 키. */
    public static long chunkKeyOfPos(long packedPos) {
        Pos p = Pos.of(packedPos);
        return chunkKey(p.x() >> 4, p.z() >> 4);
    }

    @Override
    public boolean test(long ckey) {
        int slot = (int) ((ckey * 0x9E3779B97F4A7C15L) >>> 58);
        if (used[slot] && key[slot] == ckey) return val[slot];
        boolean v = predicate.test(ckey);
        used[slot] = true;
        key[slot] = ckey;
        val[slot] = v;
        return v;
    }
}
