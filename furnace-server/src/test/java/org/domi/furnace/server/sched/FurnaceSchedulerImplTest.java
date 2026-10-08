// 파사드가 global/region 스케줄러를 올바른 owner 스레드에 연결하고
// Owned 가드가 실제 스레드 경계에서 동작하는지 통합 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.Owned;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;

class FurnaceSchedulerImplTest {

    @Test
    void global_runsOnGlobalOwner() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            Boolean onOwner = s.global().submit(
                    () -> ((GlobalThread) s.global()).isOwnerThread()).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void region_taskRunsOnRegionThread() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            RegionThread owner = s.regionThread();
            Boolean onOwner = s.region(new RegionPos(0, 0))
                    .submit(owner::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void ownedBoundToRegion_throwsFromOtherThread_butOkInsideRegion() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            RegionThread owner = s.regionThread();
            Owned<String> data = s.region(new RegionPos(0, 0))
                    .submit(() -> new Owned<>("blocks", owner)).get(2, TimeUnit.SECONDS);

            // region 스레드 안에서는 접근 OK
            String inside = s.region(new RegionPos(0, 0))
                    .submit(data::get).get(2, TimeUnit.SECONDS);
            assertEquals("blocks", inside);

            // 테스트(메인) 스레드에서 직접 접근 → AssertionError
            assertThrows(AssertionError.class, data::get);
        }
    }

    /** 종료 flush가 모든 region 스레드에 정확히 도달할 수 있어야 한다(31의 배수 스레드 수에서 위치 탐색이 실패하던 문제). */
    @Test
    void everyRegionThread_isDirectlyReachable() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L, 31)) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = 0; i < 31; i++) {
                RegionThread t = s.regionThread(i);
                assertTrue(t.submit(t::isOwnerThread).get(2, java.util.concurrent.TimeUnit.SECONDS));
                names.add(t.submit(() -> Thread.currentThread().getName()).get(2, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(31, names.size());
        }
    }
}
