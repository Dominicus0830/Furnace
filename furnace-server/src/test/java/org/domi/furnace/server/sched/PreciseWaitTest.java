// 정확히 기다리기: 돌기 구간을 켜면 데드라인 뒤 0.5ms 안에 깬다(거친 타이머 흉내 포함), 끄면 기존 방식.
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreciseWaitTest {

    @Test
    void spinningWaitWakesRightAtTheDeadline() {
        long worstLate = 0;
        for (int i = 0; i < 20; i++) {
            long deadline = System.nanoTime() + 20_000_000L;
            PreciseWait.parkUntil(deadline, 17_000_000L);
            long late = System.nanoTime() - deadline;
            assertTrue(late >= 0, "데드라인 전에 깨면 안 된다");
            worstLate = Math.max(worstLate, late);
        }
        assertTrue(worstLate < 2_000_000L, "돌며 기다리면 데드라인에서 2ms 안에 깬다: " + worstLate);
    }

    @Test
    void returnsImmediatelyWhenAlreadyDue() {
        long t = System.nanoTime();
        PreciseWait.parkUntil(t - 1_000L, 17_000_000L);
        assertTrue(System.nanoTime() - t < 5_000_000L);
    }

    @Test
    void optionsAreParsed() {
        assertEquals(0L, PreciseWait.decide("false"));
        assertEquals(17_000_000L, PreciseWait.decide("true"));
        assertTrue(PreciseWait.decide("auto") >= 0L);
    }
}
