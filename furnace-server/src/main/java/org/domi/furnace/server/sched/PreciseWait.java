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
            while (System.nanoTime() - deadline < 0) Thread.onSpinWait();   // 차이로 비교(nanoTime 은 넘어갈 수 있다)
        } else {
            LockSupport.parkNanos(remain);
        }
    }
    /**
     * deadline 까지 기다린다. 거친 구간은 parkNanos 로 자되 조기 복귀(깨우기 unpark·헛깨어남)하면 currentDeadline 을 다시 읽고
     * 남은 시간이 여전히 spinMargin 보다 크면 다시 잔다(N6 — 조기 복귀 뒤 남은 시간 전부를 돌지 않는다). 마지막 spinMargin 만 돈다.
     * currentDeadline 이 더 이른 값을 주면 그 값으로 앞당긴다. 시간 비교는 모두 차이로(nanoTime 은 음수일 수 있다).
     * 돌려주는 값: 실제로 기다린 마감.
     */
    public static long parkUntilInterruptible(long deadline, java.util.function.LongSupplier currentDeadline) {
        return parkUntilInterruptible(deadline, currentDeadline, SPIN_MARGIN_NANOS);
    }

    /** spinMargin 을 직접 준다(공유 풀·시험). */
    public static long parkUntilInterruptible(long deadline, java.util.function.LongSupplier currentDeadline, long spinMargin) {
        long d = earlier(deadline, currentDeadline.getAsLong());
        if (d == Long.MAX_VALUE) return d;                        // 기다릴 마감 없음(N4: MAX 로 차이를 계산하지 않는다)
        while (true) {                                            // (N6) 거친 구간 재park 루프
            long remain = d - System.nanoTime();
            if (remain <= spinMargin) break;
            LockSupport.parkNanos(remain - spinMargin);
            d = earlier(d, currentDeadline.getAsLong());          // 앞당겨졌으면 새 목표로, 아니면 남은 거친 구간을 다시 잔다
        }
        while (System.nanoTime() - d < 0) {
            d = earlier(d, currentDeadline.getAsLong());
            Thread.onSpinWait();
        }
        return d;
    }

    /** 둘 중 이른 마감(Long.MAX_VALUE = 없음은 가장 늦다). 차이로 비교한다. */
    public static long earlier(long a, long b) {
        if (a == Long.MAX_VALUE) return b;
        if (b == Long.MAX_VALUE) return a;
        return b - a < 0 ? b : a;
    }
}
