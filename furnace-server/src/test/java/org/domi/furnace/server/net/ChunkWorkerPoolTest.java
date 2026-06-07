// 워커풀이 작업을 풀 스레드(호출 스레드 아님)서 실행하고 결과를 반환하는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkWorkerPoolTest {

    @Test
    void submit_runsOnPoolThread_returnsResult() throws Exception {
        ChunkWorkerPool pool = new ChunkWorkerPool(2);
        try {
            String caller = Thread.currentThread().getName();
            String runner = pool.submit(() -> Thread.currentThread().getName()).get();
            assertNotEquals(caller, runner);
            assertTrue(runner.contains("chunk-worker"), runner);
        } finally {
            pool.shutdown();
        }
    }
}
