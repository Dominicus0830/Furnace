// 틱 "시작 시각" 고리 — /tps 용. 쓰는 쪽은 한 번에 한 스레드(전역 스레드, 또는 그 region 을 돌리는 워커)이고 읽는 쪽은 아무 스레드다.
// 읽기는 잠금 없이 개수(휘발성)를 먼저 읽고 칸을 읽는다 — 읽는 사이 새 틱이 옛 칸을 덮으면 더 새 값이 섞일 뿐이라 관측용으로는 충분하다.
//
// 비율 계산(rate): 창 [now-window, now] 안에 시작한 틱 k 개와, 창 바로 앞 마지막 시작(닻)을 잡아
//   틱 수 m = (닻이 있으면 k, 없으면 k-1) 을 "닻 → 가장 최근 시작" 간격으로 나눈다. 고르게 돌면 정확히 목표 값이 나온다(경계에 걸린 ±1틱 오차 없음).
//   가장 최근 시작 뒤로 평균 간격보다 오래 새 틱이 없으면(지금 멈춤) 그 초과분을 분모에 더해 멈춤이 바로 값에 드러나게 한다.
//   창 안에 틱이 하나도 없으면 0. 첫 기록부터 minHistory 가 안 지났으면 NaN(아직 잴 수 없음 — sprint 처럼 고리가 짧은 시간만 덮어도 오래된 고리면 잰다).
package org.domi.furnace.server.sched;

public final class TickStartRing {

    private final long[] ring;
    private final int mask;
    private volatile long count;
    /** 첫 기록 시각(기록 길이 판단용 — 고리가 한 바퀴 돌아도 남는다). */
    private volatile long first;

    /** size 는 2의 거듭제곱이어야 한다. 창 길이 × 최대 틱 속도보다 커야 창 전체를 덮는다(모자라면 남은 표본만으로 잰다). */
    public TickStartRing(int size) {
        if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("size 는 2의 거듭제곱: " + size);
        this.ring = new long[size];
        this.mask = size - 1;
    }

    /** 틱 하나가 startNanos 에 시작했다. 한 번에 한 스레드만 부른다. */
    public void record(long startNanos) {
        long c = count;
        if (c == 0) first = startNanos;
        ring[(int) (c & mask)] = startNanos;
        count = c + 1;   // 휘발성 쓰기 — 위 칸 쓰기를 읽는 쪽에 발행한다
    }

    /** 지금까지 기록한 틱 수. */
    public long count() { return count; }

    /** nowNanos 기준 최근 windowNanos 동안의 초당 틱 수. 위 머리말 참고. */
    public double rate(long nowNanos, long windowNanos, long minHistoryNanos) {
        long c = count;
        int n = (int) Math.min(c, ring.length);
        if (n == 0) return Double.NaN;
        long from = nowNanos - windowNanos;
        long newest = ring[(int) ((c - 1) & mask)];
        long oldest = ring[(int) ((c - n) & mask)];
        if (nowNanos - first < minHistoryNanos) return Double.NaN;
        if (newest - from <= 0) return 0.0;   // 창 안에 시작한 틱이 없다(창 길이 이상 멈춤)
        int k = 0;
        long anchor = 0;
        boolean anchored = false;
        for (int i = 0; i < n; i++) {
            long s = ring[(int) ((c - 1 - i) & mask)];
            if (s - from <= 0) {
                anchor = s;
                anchored = true;
                break;
            }
            k++;
        }
        int m;
        if (anchored) {
            m = k;
        } else {
            anchor = oldest;   // 기록이 창보다 짧다(가동 직후·새 region) 또는 고리가 창을 다 못 덮는다
            m = k - 1;
        }
        long span = newest - anchor;
        if (m <= 0 || span <= 0) return Double.NaN;
        long excess = (nowNanos - newest) - span / m;
        if (excess > 0) span += excess;
        return m * 1.0E9 / span;
    }
}
