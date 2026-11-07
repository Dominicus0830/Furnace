package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Entity.noPhysics(E3 setNoPhysics): 켜면 충돌 없이 중력대로 움직여 바닥을 뚫고 떨어지고, 끄면 바닥 위에 선다. */
class NoPhysicsRegionTest {

    @TempDir Path tmp;

    private static void until(BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (!c.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
    }

    @Test
    void noPhysicsCowFallsThroughTheFloorAndNormalCowStands() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            until(() -> world.loadedChunk(0, 0) != null, 10_000);
            world.startTicking();
            Object[] holder = new Object[2];
            var done = new CountDownLatch(1);
            world.runAt(0.5, 0.5, region -> {
                var lvl = region.level();
                var ghost = new Cow(lvl, lvl.nextEntityId());
                ghost.moveTo(2.5, -60, 2.5, 0, 0);
                ghost.noPhysics = true;
                lvl.addFreshEntity(ghost);
                var solid = new Cow(lvl, lvl.nextEntityId());
                solid.moveTo(6.5, -60, 6.5, 0, 0);
                lvl.addFreshEntity(solid);
                holder[0] = ghost;
                holder[1] = solid;
                done.countDown();
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            var ghost = (Cow) holder[0];
            var solid = (Cow) holder[1];
            until(() -> ghost.y() < -63.0 || ghost.isRemoved(), 8_000);
            assertTrue(ghost.y() < -63.0 || ghost.isRemoved(), "noPhysics 소는 바닥(y=-60 표면)을 뚫고 떨어진다: " + ghost.y());
            assertTrue(solid.y() > -61.0 && !solid.isRemoved(), "보통 소는 바닥 위에 선다: " + solid.y());
        } finally {
            sched.close();
        }
    }
}
