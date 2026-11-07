// M20: region 틱 배선 — 불 블록 안에 서면 불 틱이 오르고(checkInsideBlocks), 용암 속이면 15초 불(baseTick), 이동 끝에서 불·용암이 없으면 면역 틱.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerMoveValidator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionPlayerFireTest {

    private static PlayerMoveValidator.Check at(double x, double z) {
        return new PlayerMoveValidator.Check(true, true, x, -60, z, 0f, 0f);
    }

    @Test
    void fireBlockLavaAndLeavingFireWireThroughRegionTick() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "Burn", new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        p.publishPosition(8.5, -60, 8.5, 0, 0, true);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.chunkAtBlocking(new ChunkPos(0, 0));
            r.placeBlock(8, -60, 8, reg.defaultStateId("minecraft:fire"));
            r.placeBlock(12, -60, 12, reg.defaultStateId("minecraft:lava"));
            r.adopt(p);
        }).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(-Player.FIRE_IMMUNE_TICKS, p.remainingFireTicks(), "움직이지 않았으니 불 속이라도 checkInsideBlocks 가 돌지 않는다");

        p.pushCheckedMove(at(8.6, 8.5), true);   // 이동 패킷 → 불 블록 안
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(-Player.FIRE_IMMUNE_TICKS + 1, p.remainingFireTicks(), "불 블록 entityInside: 불 틱 +1");
        assertFalse(p.isOnFire());

        p.pushCheckedMove(at(12.5, 12.5), true);   // 용암 칸으로
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertTrue(p.isOnFire(), "용암 속: 15초 붙임");
        assertTrue(p.remainingFireTicks() > 280);
        assertEquals(1, p.sharedFlags() & 1);

        p.pushCheckedMove(at(4.5, 4.5), true);   // 용암 밖
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertTrue(p.isOnFire(), "불·용암 밖으로 나와도 붙은 불은 계속 탄다");
        io.shutdown(); sched.close();
    }
}
