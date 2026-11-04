package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** /tps·/mspt 계측: TickStartRing(틱 속도)·TickWindows(시간 창별 소요 시간)를 합성 시각으로 결정적으로. */
class TickRateMetersTest {

    private static final long MS = 1_000_000L;
    private static final long SEC = 1_000 * MS;
    private static final long T0 = 1_000 * SEC;   // 임의 기준 시각

    private static TickStartRing steady(int size, long periodNanos, int ticks) {
        TickStartRing r = new TickStartRing(size);
        for (int i = 0; i < ticks; i++) r.record(T0 + i * periodNanos);
        return r;
    }

    @Test
    void steadyTwentyIsExactlyTwentyAtAnyPhase() {
        TickStartRing r = steady(1 << 12, 50 * MS, 2000);   // 100초
        long last = T0 + 1999 * 50 * MS;
        for (long phase = 0; phase < 50 * MS; phase += 7 * MS) {
            assertEquals(20.0, r.rate(last + phase, 5 * SEC, SEC), 1e-9, "phase " + phase);
            assertEquals(20.0, r.rate(last + phase, 60 * SEC, SEC), 1e-9);
        }
    }

    @Test
    void slowRegionAndShortHistory() {
        TickStartRing r = steady(128, 100 * MS, 30);   // 10 TPS, 2.9초 기록
        long last = T0 + 29 * 100 * MS;
        assertEquals(10.0, r.rate(last + 10 * MS, 5 * SEC, SEC), 1e-9);   // 창보다 짧은 기록은 첫 시작부터
        TickStartRing young = steady(128, 50 * MS, 10);   // 0.45초 기록
        assertTrue(Double.isNaN(young.rate(T0 + 500 * MS, 5 * SEC, SEC)));
        assertTrue(Double.isNaN(new TickStartRing(8).rate(T0, 5 * SEC, SEC)));
    }

    @Test
    void ongoingStallLowersRateImmediately() {
        TickStartRing r = steady(256, 50 * MS, 200);   // 10초 동안 20 TPS 뒤 멈춤
        long last = T0 + 199 * 50 * MS;
        assertEquals(20.0, r.rate(last + 40 * MS, 5 * SEC, SEC), 1e-9);
        double stalled = r.rate(last + 2 * SEC, 5 * SEC, SEC);   // 2초째 틱이 없다
        assertTrue(stalled < 13 && stalled > 11, "멈춤 2초: " + stalled);   // 창 5초 중 3초만 돌았다 ≈ 12
        assertEquals(0.0, r.rate(last + 6 * SEC, 5 * SEC, SEC));   // 창 전체가 멈춤
    }

    @Test
    void saturatedRingUsesWhatItHolds() {
        TickStartRing r = steady(64, 10 * MS, 1000);   // 100 TPS(sprint), 고리는 0.63초분만 쥔다
        long last = T0 + 999 * 10 * MS;
        assertEquals(100.0, r.rate(last + 5 * MS, 5 * SEC, SEC), 1e-9);   // 고리는 0.63초분이지만 기록은 10초 — 잰다
        assertThrows(IllegalArgumentException.class, () -> new TickStartRing(100));
    }

    @Test
    void windowsSummariseCompleteBucketsOnly() {
        TickWindows w = new TickWindows(T0);
        assertEquals(TickWindows.Window.EMPTY, w.window(T0, 5 * SEC));
        // 10초 동안 50ms 마다, 소요 1·2·3ms 반복
        for (int i = 0; i < 200; i++) w.record(T0 + i * 50 * MS, (1 + i % 3) * MS);
        long now = T0 + 10 * SEC + 100 * MS;   // 지금 칸(10.00~10.25초)은 비어 있고 빼진다
        TickWindows.Window five = w.window(now, 5 * SEC);
        assertEquals(100, five.ticks());
        assertEquals(5 * SEC, five.spanNanos());
        assertEquals(20.0, five.tps(), 1e-9);
        assertEquals(1 * MS, five.minNanos());
        assertEquals(3 * MS, five.maxNanos());
        assertEquals(2.0 * MS, five.avgNanos(), 0.02 * MS);
        TickWindows.Window minute = w.window(now, 60 * SEC);   // 기록은 10초뿐 — 첫 칸부터 나눈다
        assertEquals(200, minute.ticks());
        assertEquals(10 * SEC, minute.spanNanos());
        // 큰 틱 하나는 시작 칸에 든다
        w.record(T0 + 10 * SEC + 300 * MS, 900 * MS);
        assertEquals(900 * MS, w.window(T0 + 11 * SEC, 5 * SEC).maxNanos());
    }

    @Test
    void windowsForgetOldBucketsAndIgnoreStaleWrites() {
        TickWindows w = new TickWindows(T0);
        w.record(T0, 40 * MS);
        long later = T0 + (TickWindows.HISTORY_NANOS / TickWindows.BUCKET_NANOS + 2) * TickWindows.BUCKET_NANOS;   // 한 바퀴 돌아 0번 칸 자리를 새 번호가 쓴다
        w.record(later, 2 * MS);
        w.record(T0 + MS, 99 * MS);   // 이미 덮인 자리의 옛 칸 — 버린다
        TickWindows.Window last = w.window(later + SEC, 5 * SEC);
        assertEquals(1, last.ticks());
        assertEquals(2 * MS, last.maxNanos());
        TickWindows.Window all = w.window(later + SEC, 900 * SEC);
        assertEquals(1, all.ticks());   // 15분 창에 옛 칸(40ms)은 들지 않는다
    }
}
