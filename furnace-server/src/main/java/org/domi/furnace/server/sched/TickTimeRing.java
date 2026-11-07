// 틱 소요 시간(ns)의 고리 버퍼 — /furnace 관측 명령용. 쓰기는 잠금 없이(칸은 원자 증가로 나눠 쓴다), 읽기는 사본을 정렬해 분위수를 낸다.
// 표본은 최근 size 개, 가동 이후 최대값은 따로 쥔다. 틱 동작에는 영향이 없다(기록 한 번 = 원자 증가 + 쓰기 한 번 + 최대값 원자 갱신).
package org.domi.furnace.server.sched;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class TickTimeRing {

    /** 분포 요약. 표본이 없으면 모두 0. */
    public record Stats(int samples, long p50Nanos, long p95Nanos, long maxRecentNanos, long maxEverNanos) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);
    }

    private final AtomicLongArray ring;
    private final int mask;
    private final AtomicLong next = new AtomicLong();
    private final AtomicLong maxEver = new AtomicLong();
    private final java.util.concurrent.atomic.LongAdder total = new java.util.concurrent.atomic.LongAdder();

    /** size 는 2의 거듭제곱이어야 한다. */
    public TickTimeRing(int size) {
        if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("size 는 2의 거듭제곱: " + size);
        this.ring = new AtomicLongArray(size);
        this.mask = size - 1;
    }

    public void record(long nanos) {
        ring.set((int) (next.getAndIncrement() & mask), nanos);
        maxEver.accumulateAndGet(nanos, Math::max);
        total.add(nanos);
    }

    /** 지금까지 기록한 틱 소요의 합(ns). */
    public long totalNanos() { return total.sum(); }

    /**
     * 최근 틱 n 개를 바닐라 MinecraftServer.tickTimesNanos 와 같은 자리에 담은 사본: 번호가 k 인 틱의 값은 [k % n]
     * (n 개가 차기 전의 빈 칸은 0). 마지막으로 기록된 틱 번호가 가장 큰 쪽이다.
     */
    public long[] recentByTickNumber(int n) {
        long[] out = new long[n];
        long total = next.get();
        long from = Math.max(0, total - Math.min(n, ring.length()));
        for (long k = from; k < total; k++) out[(int) (k % n)] = ring.get((int) (k & mask));
        return out;
    }

    /** 지금까지 기록한 틱 수. */
    public long count() { return next.get(); }

    public Stats stats() {
        int n = (int) Math.min(next.get(), ring.length());
        if (n == 0) return Stats.EMPTY;
        long[] a = new long[n];
        for (int i = 0; i < n; i++) a[i] = ring.get(i);
        Arrays.sort(a);
        return new Stats(n, a[n / 2], a[Math.min(n - 1, (int) (n * 0.95))], a[n - 1], maxEver.get());
    }
}
