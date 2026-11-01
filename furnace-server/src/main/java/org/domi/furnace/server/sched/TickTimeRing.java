// 틱 소요 시간(ns)의 고리 버퍼 — /furnace 관측 명령용. 쓰기는 잠금 없이(칸은 원자 증가로 나눠 쓴다), 읽기는 사본을 정렬해 분위수를 낸다.
// 표본은 최근 size 개, 가동 이후 최대값은 따로 쥔다. 틱 동작에는 영향이 없다(기록 한 번 = 원자 증가 + 쓰기 한 번).
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
    private volatile long maxEver;

    /** size 는 2의 거듭제곱이어야 한다. */
    public TickTimeRing(int size) {
        if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("size 는 2의 거듭제곱: " + size);
        this.ring = new AtomicLongArray(size);
        this.mask = size - 1;
    }

    public void record(long nanos) {
        ring.set((int) (next.getAndIncrement() & mask), nanos);
        if (nanos > maxEver) maxEver = nanos;
    }

    public Stats stats() {
        int n = (int) Math.min(next.get(), ring.length());
        if (n == 0) return Stats.EMPTY;
        long[] a = new long[n];
        for (int i = 0; i < n; i++) a[i] = ring.get(i);
        Arrays.sort(a);
        return new Stats(n, a[n / 2], a[Math.min(n - 1, (int) (n * 0.95))], a[n - 1], maxEver);
    }
}
