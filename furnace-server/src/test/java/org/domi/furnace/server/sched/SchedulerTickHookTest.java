// setRegionTick으로 등록한 콜백이 region 틱마다 실행되는지 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchedulerTickHookTest {

    @Test
    void setRegionTick_runsEachTick() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L); // 10ms 틱
        AtomicInteger ticks = new AtomicInteger();
        sched.setRegionTick(ticks::incrementAndGet);
        Thread.sleep(120); // ~12틱
        sched.close();
        assertTrue(ticks.get() >= 3, "ticks=" + ticks.get());
    }
}
