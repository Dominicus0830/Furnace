// 전역 owner 스레드의 실행/소유권/결과 future 동작 테스트
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GlobalThreadTest {

    @Test
    void submit_runsOnOwnerThread_andReturnsResult() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            Boolean onOwner = g.submit(g::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void callerThread_isNotOwner() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            assertFalse(g.isOwnerThread());
        }
    }

    @Test
    void execute_completesFuture() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            g.execute(() -> {}).get(2, TimeUnit.SECONDS); // 예외 없으면 통과
        }
    }

    @Test
    void waitingOnTheGlobalThreadForAGlobalTaskFailsFastInsteadOfHanging() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            // 전역 스레드 안에서 새 전역 작업의 결과를 기다린다 — 그 작업을 돌릴 스레드가 기다리는 쪽이라 영영 끝나지 않는다(G1)
            IllegalStateException e = g.submit(() -> {
                var inner = g.submit(() -> 1);
                try {
                    inner.join();
                    return null;
                } catch (IllegalStateException ex) {
                    return ex;
                }
            }).get(5, TimeUnit.SECONDS);
            assertNotNull(e, "join() 이 곧바로 던져야 한다");
            assertTrue(e.getMessage().contains("global thread") && e.getMessage().contains("thenCompose"), e.getMessage());
            // get·get(timeout) 도 같다
            Exception viaGet = g.submit(() -> {
                try {
                    g.submit(() -> 1).get();
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            }).get(5, TimeUnit.SECONDS);
            assertInstanceOf(IllegalStateException.class, viaGet);
            // 이미 끝난 결과는 전역 스레드에서도 기다려도 된다(다른 스레드에서 기다리는 것은 위 get 들이 그렇다)
            assertEquals(5, g.submit(() -> {
                var done = g.submit(() -> 2);
                done.complete(2);
                return done.join() + 3;
            }).get(5, TimeUnit.SECONDS));
            assertEquals(3, g.submit(() -> 3).get(5, TimeUnit.SECONDS));
        }
    }
}
