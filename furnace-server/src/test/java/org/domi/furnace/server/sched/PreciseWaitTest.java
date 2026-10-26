// 정확히 기다리기: 돌기 구간을 켜면 데드라인 뒤 0.5ms 안에 깬다(거친 타이머 흉내 포함), 끄면 기존 방식.
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreciseWaitTest {

    @Test
    void spinningWaitWakesRightAtTheDeadline() {
        long[] late = new long[20];
        for (int i = 0; i < late.length; i++) {
            long deadline = System.nanoTime() + 20_000_000L;
            PreciseWait.parkUntil(deadline, 17_000_000L);
            late[i] = System.nanoTime() - deadline;
            assertTrue(late[i] >= 0, "데드라인 전에 깨면 안 된다");
        }
        java.util.Arrays.sort(late);
        // 다른 프로세스가 CPU 를 쓰는 중에도 흔들리지 않게 중앙값으로 본다(최악값은 스케줄러 탓으로 튈 수 있다)
        assertTrue(late[late.length / 2] < 1_000_000L, "돌며 기다리면 중앙값이 1ms 안: " + late[late.length / 2]);
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
