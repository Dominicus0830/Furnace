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
}
