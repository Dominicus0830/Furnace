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
    /** 기다리는 중 마감이 앞당겨지고 unpark 되면 새 마감에 돌아오고, 그 마감을 돌려준다(리눅스·거친 타이머 둘 다). */
    @Test
    void interruptibleWaitFollowsAnEarlierDeadline() throws Exception {
        for (long margin : new long[] {0L, 17_000_000L}) {
            long t0 = System.nanoTime();
            java.util.concurrent.atomic.AtomicLong cur = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
            long[] got = new long[1];
            Thread waiter = new Thread(() -> got[0] = PreciseWait.parkUntilInterruptible(t0 + 40_000_000L, cur::get, margin));
            waiter.start();
            Thread.sleep(2);
            long early = System.nanoTime() + 3_000_000L;
            cur.set(early);
            java.util.concurrent.locks.LockSupport.unpark(waiter);
            waiter.join(5000);
            long back = System.nanoTime();
            assertEquals(early, got[0], "앞당긴 마감을 돌려준다(margin=" + margin + ")");
            assertTrue(back - t0 < 30_000_000L, "앞당긴 마감 근처에 돌아온다: " + (back - t0) / 1_000_000 + "ms");
        }
    }

    /**
     * (N6) 30ms 목표, 거친 타이머 흉내(spin=17ms)에서 5ms 마다 헛 unpark: 조기 복귀 뒤에도 다시 자서, 도는 구간은 마지막 17ms 뿐이다.
     * 마감 공급자 호출 시각으로 본다 — 자는 동안에는 깰 때마다 한 번, 도는 동안에는 계속 불린다. 13ms 이전 호출은 헛깨어남 수(≤ 3) 정도.
     */
    @Test
    void earlyReturnParksAgainInsteadOfSpinning() throws Exception {
        long t0 = System.nanoTime();
        long deadline = t0 + 30_000_000L;
        java.util.concurrent.atomic.AtomicInteger callsBefore12ms = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.LongSupplier cur = () -> {
            if (System.nanoTime() - t0 < 12_000_000L) callsBefore12ms.incrementAndGet();
            return Long.MAX_VALUE;
        };
        Thread waiter = new Thread(() -> PreciseWait.parkUntilInterruptible(deadline, cur, 17_000_000L));
        waiter.start();
        for (int i = 0; i < 6; i++) {
            Thread.sleep(5);
            java.util.concurrent.locks.LockSupport.unpark(waiter);
        }
        waiter.join(5000);
        assertTrue(System.nanoTime() - deadline >= 0, "마감 전에 돌아오면 안 된다");
        assertTrue(callsBefore12ms.get() <= 10, "헛깨어남 뒤 돌기 시작했다(조기 스핀): 12ms 전 호출 " + callsBefore12ms.get());
    }

    /** (N4) Long.MAX_VALUE = 마감 없음: 오버플로 없이 곧바로 돌아오고, 비교는 nanoTime 이 넘어가는 경계에서도 차이로 맞다. */
    @Test
    void maxDeadlineAndWrapAroundComparisons() {
        long t = System.nanoTime();
        assertEquals(Long.MAX_VALUE, PreciseWait.parkUntilInterruptible(Long.MAX_VALUE, () -> Long.MAX_VALUE, 17_000_000L));
        assertTrue(System.nanoTime() - t < 5_000_000L, "MAX 는 기다리지 않는다");
        assertEquals(5L, PreciseWait.earlier(Long.MAX_VALUE, 5L));
        assertEquals(5L, PreciseWait.earlier(5L, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, PreciseWait.earlier(Long.MAX_VALUE, Long.MAX_VALUE));
        long beforeWrap = Long.MAX_VALUE - 10, afterWrap = Long.MIN_VALUE + 10;   // afterWrap 이 21ns 뒤
        assertEquals(beforeWrap, PreciseWait.earlier(beforeWrap, afterWrap));
        assertEquals(beforeWrap, PreciseWait.earlier(afterWrap, beforeWrap));
        assertEquals(Long.MIN_VALUE, PreciseWait.earlier(Long.MIN_VALUE, Long.MIN_VALUE + 1));
    }
}
