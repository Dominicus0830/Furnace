// 플레이어의 entityInside: 용암 가마솥(lavaHurt), 물 가마솥(불 끄기 + 한 단계 줄임), 가루눈(불붙은 플레이어가 녹임), 가마솥 밖은 그대로.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionPlayerCauldronTest {

    private interface Setup { void run(Region r, BlockRegistry reg); }

    private record Rig(FurnaceSchedulerImpl sched, World w, RegionPos rp, Player p, BlockRegistry reg, java.util.concurrent.ExecutorService io) {
        void tick(int n) throws Exception {
            for (int i = 0; i < n; i++) sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        }
        int state(int x, int y, int z) throws Exception {
            int[] out = new int[1];
            sched.region(rp).execute(() -> out[0] = w.regionAt(rp).level().getBlockState(new Pos(x, y, z))).get();
            return out[0];
        }
        void close() { io.shutdown(); sched.close(); }
    }

    /** (8,-60,8) 칸에 놓고 플레이어가 그 칸 가운데에 선 채로 두 틱을 돌린다(첫 틱은 발행된 위치가 틱 끝에서 적용된다). */
    private static Rig rig(String block, Setup after) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "Cauldron", new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        p.publishPosition(8.5, -60, 8.5, 0, 0, true);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.chunkAtBlocking(new ChunkPos(0, 0));
            r.placeBlock(8, -60, 8, reg.defaultStateId(block));
            if (after != null) after.run(r, reg);
            r.adopt(p);
        }).get();
        return new Rig(sched, w, rp, p, reg, io);
    }

    @Test
    void lavaCauldronBurnsStandingPlayer() throws Exception {
        Rig g = rig("minecraft:lava_cauldron", null);
        g.tick(2);
        assertTrue(g.p().isOnFire(), "용암 가마솥 안: lavaHurt 가 15초 불을 붙인다");
        assertTrue(g.p().remainingFireTicks() > 200);
        g.close();
    }

    @Test
    void emptyCauldronDoesNothing() throws Exception {
        Rig g = rig("minecraft:cauldron", null);
        g.tick(2);
        assertFalse(g.p().isOnFire());
        g.close();
    }

    @Test
    void waterCauldronExtinguishesBurningPlayerAndLowersLevel() throws Exception {
        Rig g = rig("minecraft:water_cauldron", null);
        g.p().setRemainingFireTicks(100);
        g.tick(2);
        assertFalse(g.p().isOnFire(), "불이 꺼진다");
        assertEquals(g.reg().defaultStateId("minecraft:cauldron"), g.state(8, -60, 8), "level 1 → 빈 가마솥");
        g.close();
    }

    @Test
    void waterCauldronLeavesNonBurningPlayerAlone() throws Exception {
        Rig g = rig("minecraft:water_cauldron", null);
        g.tick(2);
        assertEquals(g.reg().defaultStateId("minecraft:water_cauldron"), g.state(8, -60, 8));
        g.close();
    }

    @Test
    void burningPlayerMeltsPowderSnow() throws Exception {
        Rig g = rig("minecraft:powder_snow", null);
        g.p().setRemainingFireTicks(100);
        g.tick(2);
        assertEquals(g.reg().defaultStateId("minecraft:air"), g.state(8, -60, 8), "불붙은 플레이어가 가루눈을 녹인다");
        g.close();
    }

    @Test
    void coldPlayerKeepsPowderSnow() throws Exception {
        Rig g = rig("minecraft:powder_snow", null);
        g.tick(2);
        assertEquals(g.reg().defaultStateId("minecraft:powder_snow"), g.state(8, -60, 8));
        g.close();
    }
}
