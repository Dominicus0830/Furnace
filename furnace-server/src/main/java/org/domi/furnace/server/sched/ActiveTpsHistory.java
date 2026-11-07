// "활성 region 평균 TPS" 의 시간 창 평균 — getTPS()/ /tps 의 1분·5분·15분 값용.
// region 은 합쳐지고 나뉘어 오래 이어지는 기록을 가질 수 없으므로(region 고리는 6.4초), 전역 스레드가 1초마다 그 순간의 평균을 한 표본으로 적어 두고
// 창 평균은 표본의 단순 평균으로 낸다. 쓰기는 전역 스레드, 읽기는 아무 스레드(짧은 잠금).
package org.domi.furnace.server.sched;

public final class ActiveTpsHistory {

    /** 표본 간격(1초)과 쥐는 길이(15분 + 여유). */
    public static final long SAMPLE_NANOS = 1_000_000_000L;
    private static final int CAPACITY = 16 * 60;

    private final long[] times = new long[CAPACITY];
    private final double[] values = new double[CAPACITY];
    private int count;      // 쓴 표본 수(단조)
    private long lastSample = Long.MIN_VALUE;

    /** 마지막 표본 뒤 1초가 지났으면 true. (전역 스레드가 부르고, 참이면 record 한다.) */
    public synchronized boolean due(long now) {
        return lastSample == Long.MIN_VALUE || now - lastSample >= SAMPLE_NANOS;
    }

    /** 표본 하나(NaN 은 버린다 — 잴 수 없던 순간). */
    public synchronized void record(long now, double tps) {
        lastSample = now;
        if (Double.isNaN(tps)) return;
        int i = count % CAPACITY;
        times[i] = now;
        values[i] = tps;
        count++;
    }

    /** now 기준 최근 windowNanos 안 표본의 평균. 표본이 하나도 없으면 NaN. */
    public synchronized double average(long now, long windowNanos) {
        double sum = 0;
        int n = 0;
        int avail = Math.min(count, CAPACITY);
        for (int k = 0; k < avail; k++) {
            int i = ((count - 1 - k) % CAPACITY + CAPACITY) % CAPACITY;
            if (now - times[i] > windowNanos) break;
            sum += values[i];
            n++;
        }
        return n == 0 ? Double.NaN : sum / n;
    }
}
