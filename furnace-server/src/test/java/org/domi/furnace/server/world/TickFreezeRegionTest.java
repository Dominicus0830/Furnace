// /tick freeze: 정지 중인 region 은 엔티티를 틱하지 않고(소가 떨어지지 않는다), step 은 정확히 N 번 진행하며, 해제하면 다시 틱한다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TickFreezeRegionTest {

    @TempDir Path tmp;

    private static void until(java.util.function.BooleanSupplier c, int ms) throws InterruptedException {
        for (int i = 0; i < ms / 20 && !c.getAsBoolean(); i++) Thread.sleep(20);
    }

    @Test
    void forcedChunkWithoutPlayerGetsNoRandomTickOrInhabitedTime() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            until(() -> world.loadedChunk(0, 0) != null, 10_000);
            world.startTicking();
            Thread.sleep(800);   // 틱이 열 번 넘게 돈다
            assertEquals(0L, world.loadedChunk(0, 0).inhabitedTime(), "바닐라 tickChunks 는 플레이어 128 블록 안 청크만 inhabitedTime 을 올린다");
        } finally {
            sched.close();
        }
    }

    @Test
    void newRegionDuringStepDoesNotReceiveTheStep() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            var ts = core.tickState();
            ts.setFrozen(true);
            ts.startStep(7);
            Region[] holder = new Region[1];
            var done = new CountDownLatch(1);
            world.runAt(16000.5, 16000.5, r -> {
                holder[0] = r;
                done.countDown();
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(ts.generation(), holder[0].stepGeneration());
            assertEquals(0, holder[0].stepsLeft(), "step 도중 생긴 region 은 N 번을 처음부터 받지 않는다");
            holder[0].inheritSteps(ts.generation(), 3);   // 나뉜 조각: 부모의 남은 걸음 3
            assertEquals(3, holder[0].stepsLeft());
        } finally {
            sched.close();
        }
    }

    @Test
    void frozenRegionDoesNotTickEntities() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);   // 플레이어 없이도 올라와 있게
            until(() -> world.loadedChunk(0, 0) != null, 10_000);
            assertNotNull(world.loadedChunk(0, 0));
            world.startTicking();
            Cow[] holder = new Cow[1];
            var done = new CountDownLatch(1);
            world.runAt(0.5, 0.5, region -> {
                var lvl = region.level();
                holder[0] = new Cow(lvl, lvl.nextEntityId());
                holder[0].moveTo(0.5, -20, 0.5, 0, 0);   // 지면(-61) 위 공중: 틱하면 떨어진다
                lvl.addFreshEntity(holder[0]);
                done.countDown();
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            Cow cow = holder[0];
            var console = CommandSource.console(core);
            var d = core.commands().getDispatcher();
            until(() -> cow.y() < -20.5, 5_000);
            assertTrue(cow.y() < -20.5, "정지 전에는 떨어진다: " + cow.y());
            d.execute("tick freeze", console);
            Thread.sleep(300);   // 진행 중이던 틱이 끝나기를 기다린다
            double frozenY = cow.y();
            Thread.sleep(600);
            assertEquals(frozenY, cow.y(), 0.0, "정지 중에는 엔티티가 움직이지 않는다");
            // step 1: 정확히 한 번 더 틱한 뒤 다시 멈춘다
            d.execute("tick step 1", console);
            until(() -> cow.y() != frozenY, 3_000);
            double afterStep = cow.y();
            assertNotEquals(frozenY, afterStep);
            Thread.sleep(500);
            assertEquals(afterStep, cow.y(), 0.0, "step 이 끝나면 다시 멈춘다");
            // 해제하면 다시 떨어진다
            d.execute("tick unfreeze", console);
            until(() -> cow.y() < afterStep - 0.5, 5_000);
            assertTrue(cow.y() < afterStep - 0.5, "해제하면 다시 틱한다");
        } finally {
            sched.close();
        }
    }
}
