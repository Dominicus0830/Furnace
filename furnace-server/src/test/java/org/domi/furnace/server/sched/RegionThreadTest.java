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

    /** 인박스를 계속 채우는(자기 자신을 재제출하는) 작업이 있어도 틱 루프가 굶으면 안 된다. */
    @Test
    void selfFeedingInbox_doesNotStarveTicks() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, ticks::incrementAndGet)) {
            Runnable[] feeder = new Runnable[1];
            feeder[0] = () -> r.execute(feeder[0]);
            r.execute(feeder[0]);
            Thread.sleep(600);
            assertTrue(ticks.get() >= 3, "인박스 폭주로 틱이 굶었다: ticks=" + ticks.get());
        }
    }
}
