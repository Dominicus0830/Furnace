// I-4: 같은 region 파일의 IO 작업은 FIFO, 다른 파일은 서로 막지 않는다. 바탕 풀이 닫혀도 순서대로 처리.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class IoLanesTest {

    @Test
    void sameRegionFile_sameLane_differentFile_canDiffer() {
        IoLanes lanes = new IoLanes(Runnable::run, 16);
        assertSame(lanes.lane(0, 0), lanes.lane(31, 31));
        assertSame(lanes.lane(-1, -1), lanes.lane(-32, -32));
        boolean differs = false;
        for (int i = 1; i < 64 && !differs; i++) differs = lanes.lane(0, 0) != lanes.lane(i * 32, 0);
        assertTrue(differs, "샤딩이 한 레인으로 몰리면 안 된다");
    }

    @Test
    void saveThenLoad_sameFile_isFifo_evenWhenSaveIsSlow() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        IoLanes lanes = new IoLanes(pool, 4);
        CountDownLatch gate = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        lanes.lane(3, 3).execute(() -> {
            try { gate.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            order.add("save");
        });
        CountDownLatch loaded = new CountDownLatch(1);
        lanes.lane(5, 7).execute(() -> { order.add("load"); loaded.countDown(); });   // 같은 r.0.0.mca
        // 다른 파일 작업은 막힌 레인과 무관하게 돈다(같은 레인으로 샤딩되지 않는 파일을 고른다)
        int other = 1;
        while (lanes.lane(other * 32, 0) == lanes.lane(0, 0)) other++;
        CountDownLatch otherDone = new CountDownLatch(1);
        lanes.lane(other * 32, 0).execute(otherDone::countDown);
        assertTrue(otherDone.await(5, TimeUnit.SECONDS), "다른 파일 레인이 막혔다");
        assertFalse(loaded.await(100, TimeUnit.MILLISECONDS), "load 가 앞선 save 를 앞질렀다");
        gate.countDown();
        assertTrue(loaded.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("save", "load"), order);
        pool.shutdown();
    }

    @Test
    void manyTasks_perLaneOrderPreserved() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        IoLanes lanes = new IoLanes(pool, 3);
        int files = 6, per = 2000;
        List<List<Integer>> seen = new ArrayList<>();
        for (int f = 0; f < files; f++) seen.add(Collections.synchronizedList(new ArrayList<>()));
        for (int i = 0; i < per; i++) {
            for (int f = 0; f < files; f++) {
                int ff = f, ii = i;
                lanes.lane(f * 32, 0).execute(() -> seen.get(ff).add(ii));
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "풀 종료가 레인을 비워야 한다");
        for (int f = 0; f < files; f++) {
            List<Integer> s = seen.get(f);
            assertEquals(per, s.size());
            for (int i = 0; i < per; i++) assertEquals(i, s.get(i));
        }
    }

    @Test
    void rejectedBackingPool_runsInlineInOrder() {
        var pool = Executors.newSingleThreadExecutor();
        pool.shutdown();
        IoLanes lanes = new IoLanes(pool, 2);
        List<Integer> seen = new ArrayList<>();
        lanes.lane(0, 0).execute(() -> seen.add(1));
        lanes.lane(0, 0).execute(() -> seen.add(2));
        assertEquals(List.of(1, 2), seen);
    }
}
