// 시간 창별 틱 통계 — /tps·/mspt 용. 틱을 "시작 시각"이 든 칸(BUCKET_NANOS 단위)에 모아 칸마다 개수·소요 합·최소·최대를 쥔다.
// 창 하나 = 지금 칸 바로 앞까지의 "다 찬" 칸들(지금 칸은 아직 차는 중이라 뺀다 — 그래서 값이 최대 한 칸만큼 늦다).
// 가동 직후처럼 표본이 창보다 짧으면 첫 기록 칸부터의 길이로 나눈다. 쓰기는 여러 워커가 동시에 하므로 잠금 하나로 묶는다
// (기록 한 번 = 나눗셈 한 번 + 칸 갱신, 틱 수십~수백 회/초 수준이라 경합은 무시할 만하다). 틱 동작에는 영향이 없다.
package org.domi.furnace.server.sched;

public final class TickWindows {

    /** 칸 길이: 0.25초. 5초 창 오차가 칸 하나(5%)를 넘지 않도록 1초보다 잘게 나눈다. */
    public static final long BUCKET_NANOS = 250_000_000L;
    /** 쥐는 길이: 15분(/tps 의 가장 긴 창). */
    public static final long HISTORY_NANOS = 15L * 60 * 1_000_000_000L;

    /** 창 하나의 요약. ticks 가 0 이면 소요 값은 모두 0. spanNanos 는 나눈 길이(0 이면 아직 다 찬 칸이 없다). */
    public record Window(long ticks, long spanNanos, long sumNanos, long minNanos, long maxNanos) {
        public static final Window EMPTY = new Window(0, 0, 0, 0, 0);

        /** 초당 틱 수. 다 찬 칸이 없으면 NaN. */
        public double tps() { return spanNanos == 0 ? Double.NaN : ticks * 1.0E9 / spanNanos; }

        /** 평균 소요(ns). 틱이 없으면 0. */
        public double avgNanos() { return ticks == 0 ? 0 : (double) sumNanos / ticks; }
    }

    private final long origin;
    private final int size;
    /** 칸 번호(origin 기준). -1 = 빈 칸. 같은 자리를 새 번호가 쓰면 옛 값을 버린다. */
    private final long[] id, count, sum, min, max;
    /** 첫 기록 칸 번호(-1 = 기록 없음). */
    private long firstBucket = -1;

    public TickWindows(long originNanos) {
        this.origin = originNanos;
        this.size = (int) (HISTORY_NANOS / BUCKET_NANOS) + 2;
        this.id = new long[size];
        this.count = new long[size];
        this.sum = new long[size];
        this.min = new long[size];
        this.max = new long[size];
        java.util.Arrays.fill(id, -1);
    }

    public TickWindows() { this(System.nanoTime()); }

    private long bucketOf(long nanos) { return Math.floorDiv(nanos - origin, BUCKET_NANOS); }

    /** 만든 뒤 기록한 틱 수와 소요 합(ns) — /perf 가 구간 차이를 낸다. 창과 달리 버리지 않는다. */
    private long totalTicks, totalNanos;

    public synchronized long totalTicks() { return totalTicks; }

    public synchronized long totalNanos() { return totalNanos; }

    /** 틱 하나: startNanos 에 시작해 durationNanos 걸렸다. */
    public synchronized void record(long startNanos, long durationNanos) {
        totalTicks++;
        totalNanos += durationNanos;
        long b = bucketOf(startNanos);
        if (b < 0) return;   // origin 앞(만들기 전에 시작한 틱) — 버린다
        int i = (int) (b % size);
        if (id[i] != b) {
            if (id[i] > b) return;   // 이미 더 새 칸이 쓴 자리(아주 늦게 끝난 틱) — 버린다
            id[i] = b;
            count[i] = 0;
            sum[i] = 0;
            min[i] = Long.MAX_VALUE;
            max[i] = 0;
        }
        count[i]++;
        sum[i] += durationNanos;
        if (durationNanos < min[i]) min[i] = durationNanos;
        if (durationNanos > max[i]) max[i] = durationNanos;
        if (firstBucket < 0 || b < firstBucket) firstBucket = b;
    }

    /** nowNanos 기준 최근 windowNanos 동안(다 찬 칸만)의 요약. */
    public synchronized Window window(long nowNanos, long windowNanos) {
        if (firstBucket < 0) return Window.EMPTY;
        long cur = bucketOf(nowNanos);
        long want = Math.min(Math.max(1, windowNanos / BUCKET_NANOS), size - 2);
        long from = Math.max(cur - want, firstBucket);
        if (from >= cur) return Window.EMPTY;
        long n = 0, s = 0, lo = Long.MAX_VALUE, hi = 0;
        for (long b = from; b < cur; b++) {
            int i = (int) (b % size);
            if (id[i] != b) continue;   // 틱이 하나도 시작하지 않은 칸(멈춤)
            n += count[i];
            s += sum[i];
            lo = Math.min(lo, min[i]);
            hi = Math.max(hi, max[i]);
        }
        return new Window(n, (cur - from) * BUCKET_NANOS, s, n == 0 ? 0 : lo, hi);
    }
}
