// 틱 타이머의 드리프트 보정 동작 테스트(주입된 가상 시간 사용, 실시간 의존 없음)
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TickTimerTest {

    private static final long T = 50_000_000L; // 50ms

    @Test
    void remaining_isFullInterval_afterStart() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);
        assertEquals(T, timer.remaining(0));
    }

    @Test
    void advance_keepsBoundary_whenTickRanLate() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);            // 다음 경계 = 50ms
        timer.advance(60_000_000); // 틱이 60ms에 끝남(10ms 지연) → 다음 경계 100ms
        // 드리프트 보정: 100ms 경계 유지, 60ms 시점에서 40ms 남음
        assertEquals(40_000_000L, timer.remaining(60_000_000));
    }

    @Test
    void advance_resyncs_whenFarBehind() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);             // 다음 경계 = 50ms
        timer.advance(200_000_000); // 200ms에 끝남(스파이럴) → 경계 100ms 이미 지남
        // anti-spiral 재동기: 200ms 기준 한 틱 뒤
        assertEquals(T, timer.remaining(200_000_000));
    }
}
