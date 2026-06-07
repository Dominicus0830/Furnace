// region(pos)가 결정적으로 스레드에 매핑되고, setRegionTick이 인덱스별로 도는지 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;
import org.junit.jupiter.api.Test;

class RegionRoutingTest {

    @Test
    void region_mapsDeterministically() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        TaskScheduler a = sched.region(new RegionPos(1, 2));
        TaskScheduler b = sched.region(new RegionPos(1, 2));
        assertSame(a, b);  // 같은 pos → 같은 스레드
        sched.close();
    }

    @Test
    void setRegionTick_passesThreadIndex() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        sched.setRegionTick(seen::add);
        Thread.sleep(120);
        sched.close();
        assertEquals(Set.of(0, 1, 2), seen); // 3개 인덱스 모두 틱
    }
}
