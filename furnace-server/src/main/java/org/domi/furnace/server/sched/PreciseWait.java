// 틱 경계까지 정확히 기다리기. 윈도처럼 대기 해상도가 거친(약 15.6ms) 플랫폼에서는 parkNanos 가 요청보다 길게 자서
// 틱 간격이 47ms/63ms 로 갈라지고 몹 움직임이 뚝뚝 끊겨 보인다 — 그런 곳에서는 마지막 구간만 짧게 돌며(spin) 기다린다.
package org.domi.furnace.server.sched;

import java.util.concurrent.locks.LockSupport;

public final class PreciseWait {
    private PreciseWait() { }

    /** 마지막으로 돌며 기다릴 구간(ns). 0 이면 돌지 않는다(대기가 정확한 플랫폼). -Dfurnace.timer.spin=true|false|auto(기본). */
    private static final long SPIN_MARGIN_NANOS = decide(System.getProperty("furnace.timer.spin", "auto"));

    static long decide(String option) {
        return switch (option) {
            case "true" -> 17_000_000L;
            case "false" -> 0L;
            default -> calibrate();
        };
    }

    /**
     * parkNanos(1ms) 를 여러 번 재서 중앙값이 3ms 를 넘으면 거친 타이머로 본다(일시적인 CPU 혼잡에 속지 않게 최댓값이 아니라 중앙값).
     * 돌 구간은 최악 지연 + 2ms(최대 20ms).
     */
    private static long calibrate() {
        long[] d = new long[15];
        for (int i = 0; i < d.length; i++) {
            long t = System.nanoTime();
            LockSupport.parkNanos(1_000_000L);
            d[i] = System.nanoTime() - t;
        }
        java.util.Arrays.sort(d);
        return d[d.length / 2] > 3_000_000L ? Math.min(20_000_000L, d[d.length - 1] + 2_000_000L) : 0L;
    }

    /** 돌며 기다릴 구간(ns). */
    public static long spinMargin() { return SPIN_MARGIN_NANOS; }

    /** deadline(System.nanoTime 기준)까지 기다린다. 거친 구간은 자고 마지막 spinMargin 만 돈다. */
    public static void parkUntil(long deadline) { parkUntil(deadline, SPIN_MARGIN_NANOS); }

    static void parkUntil(long deadline, long spinMargin) {
        long remain = deadline - System.nanoTime();
        if (remain <= 0L) return;
        if (spinMargin > 0L && remain > spinMargin) LockSupport.parkNanos(remain - spinMargin);
        if (spinMargin > 0L) {
            while (System.nanoTime() < deadline) Thread.onSpinWait();
        } else {
            LockSupport.parkNanos(remain);
        }
    }
}
