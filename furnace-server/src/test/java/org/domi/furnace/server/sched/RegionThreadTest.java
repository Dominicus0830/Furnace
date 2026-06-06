// region owner 스레드의 틱/큐드레인/소유권 동작 테스트
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegionThreadTest {

    @Test
    void submit_runsOnOwnerThread() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Boolean onOwner = r.submit(r::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void perTick_runsRepeatedly() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, ticks::incrementAndGet)) {
            Thread.sleep(300); // ~6틱
            assertTrue(ticks.get() >= 3, "perTick 호출 수=" + ticks.get());
        }
    }

    @Test
    void tickCount_increases() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Thread.sleep(300);
            assertTrue(r.tickCount() >= 3, "tickCount=" + r.tickCount());
        }
    }
}
