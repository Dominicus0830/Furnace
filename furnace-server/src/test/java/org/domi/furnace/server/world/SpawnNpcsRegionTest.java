package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** spawn-npcs=false: 틱 때 Npc(주민)를 지우고 다른 몹은 그대로 둔다(ServerLevel.shouldDiscardEntity). */
class SpawnNpcsRegionTest {

    @TempDir Path tmp;

    private static void until(BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (!c.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
    }

    @Test
    void villagersAreDiscardedButCowsStay() throws Exception {
        Files.writeString(tmp.resolve("server.properties"), "spawn-npcs=false\n");
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
                var v = new Villager(lvl, lvl.nextEntityId());
                v.moveTo(0.5, -60, 0.5, 0, 0);
                lvl.addFreshEntity(v);
                var c = new Cow(lvl, lvl.nextEntityId());
                c.moveTo(2.5, -60, 0.5, 0, 0);
                lvl.addFreshEntity(c);
                holder[0] = v;
                holder[1] = c;
                done.countDown();
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            var villager = (Villager) holder[0];
            var cow = (Cow) holder[1];
            until(villager::isRemoved, 5_000);
            assertTrue(villager.isRemoved(), "spawn-npcs=false 면 주민은 다음 틱에 지워진다");
            assertFalse(cow.isRemoved());
        } finally {
            sched.close();
        }
    }

}
