// A03-03 회귀(2차 점검 재현 시험): /tick freeze 중 /tick step N 은 바닐라에서 모든 엔티티를 정확히 N 틱 진행한다. Furnace 는 region 마다
// 남은 걸음을 따로 센다(Region.stepsLeft). step 도중 새로 생긴 region(남은 걸음 0, 참여 안 함)이 이미 있던 region 을 흡수해도 참여 쪽
// 값을 쓰므로(Region.mergeStepsFrom) 이미 있던 엔티티는 정확히 N 걸음을 받는다 — 잃지도 더 받지도 않는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StepMergeTest {

    @TempDir Path tmp;
    static final int STEPS = 40;

    private static Cow spawnCow(World w, int cx) throws Exception {
        return w.submitAtChunk(new ChunkPos(cx, 0), r -> {
            var lvl = r.level();
            Cow c = new Cow(lvl, lvl.nextEntityId());
            c.setNoAi(true);
            c.setNoGravity(true);
            c.moveTo(cx * 16 + 8.5, 0, 8.5, 0, 0);
            lvl.addFreshEntity(c);
            return c;
        }).get(10, TimeUnit.SECONDS);
    }

    private static void force(World w, int cx) throws Exception {
        w.setChunkForced(cx, 0, true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.loadedChunk(cx, 0) == null) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }

    @Test
    void stepCountSurvivesMerge() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World w = core.world();
            var ts = core.tickState();
            w.startTicking();
            List<String> results = new ArrayList<>();
            int wrong = 0, over = 0;
            for (int trial = 0; trial < 6; trial++) {
                int base = 200 + trial * 100;   // 구역(16청크) 단위로 서로 멀리
                force(w, base);
                Cow old = spawnCow(w, base);
                ts.setFrozen(true);
                Thread.sleep(200);
                int old0 = old.tickCount;
                ts.startStep(STEPS);
                Thread.sleep(500);                 // 약 10걸음 진행
                force(w, base + 32);               // step 도중 새 region(남은 걸음 0)
                Cow fresh = spawnCow(w, base + 32);
                int fresh0 = fresh.tickCount;
                int oldBeforeMerge = old.tickCount - old0;
                force(w, base + 16);               // 다리 구역 → 두 region 합치기
                Thread.sleep(STEPS * 50L + 1_500); // step 이 끝나고도 남게
                int dOld = old.tickCount - old0, dFresh = fresh.tickCount - fresh0;
                String r = String.format("trial %d: 원래 소 %d걸음(합치기 전 %d, 바닐라 %d), 새 소 %d걸음(문서: 0, 바닐라 ≈%d)",
                        trial, dOld, oldBeforeMerge, STEPS, dFresh, STEPS - oldBeforeMerge);
                results.add(r);
                if (dOld != STEPS) wrong++;
                if (dFresh > STEPS) over++;
                ts.setFrozen(false);
                Thread.sleep(100);
            }
            String summary = "A03-STEP 6회 중 원래 엔티티 걸음이 N 이 아님 " + wrong + "회, 새 엔티티가 N 보다 더 받음 " + over + "회\n  " + String.join("\n  ", results);
            System.out.println(summary);
            assertEquals(0, wrong, summary);
            assertEquals(0, over, summary);
        } finally {
            sched.close();
        }
    }
}
